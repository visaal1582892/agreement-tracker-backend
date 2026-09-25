package com.medplus.agreement_tracker_backend.service.commercial;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutLineItem;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.CommercialCalculationDao.PurchaseTotals;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.EarnedPayoutCalculationService;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.MutableJbpLineItem;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.PeriodDetectionService;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.PurchaseAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * JBP (Commercial Contracts). Every configured period (both master and sub-periods) is evaluated as
 * its own line item. Upward contagion uses calendar month containment (e.g. May ⊂ MAM ⊂ Year), not
 * the database parent_time_period link. Any child with qualifierMet=false (target miss = qualifier
 * never reached, or explicit qualifier miss) forces its immediate calendar parent to zero and
 * cascades bottom-up.
 */
@Component
@RequiredArgsConstructor
public class JbpCalculationStrategy implements CommercialCalculationStrategy {

    private static final String SEGMENT_FLAT = "JBP_FLAT";
    private static final String STATUS_CHILD_QUALIFIER_MISSED = "Child Qualifier Missed";

    private final AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
    private final FlatBaselinePayoutCalculator flatBaselinePayoutCalculator;
    
    // New injected services for Top-Down Orchestration
    private final PeriodDetectionService periodDetectionService;
    private final PurchaseAggregationService purchaseAggregationService;
    private final EarnedPayoutCalculationService earnedPayoutCalculationService;

    @Override
    public String supportedIncomeType() {
        return IncomeTypeNames.COMMERCIAL_CONTRACTS;
    }

    @Override
    public CommercialPayoutResponse calculatePayout(CommercialCalculationContext context) {
        AgreementVersion version = context.version();
        List<String> notes = new ArrayList<>();

        List<AgreementJbpCommercialPeriod> allRows =
                jbpCommercialPeriodRepository.findHydrationRowsByAgreementVersionId(version.getId());

        if (allRows.isEmpty()) {
            return calculateFlatBaseline(context, notes);
        }

        // STEP 1: Detect Periods (PeriodDetectionService)
        Map<String, AgreementJbpCommercialPeriod> rowByCell = periodDetectionService.indexByCell(allRows);
        Map<String, List<AgreementJbpCommercialPeriod>> ladders = periodDetectionService.groupByConfigAndPeriod(allRows);

        List<MutableJbpLineItem> mutableItems = new ArrayList<>();
        for (List<AgreementJbpCommercialPeriod> ladder : ladders.values()) {
            AgreementJbpCommercialPeriod anyRow = ladder.get(0);
            
            // Overlap check
            Set<Integer> periodMonthKeys = new TreeSet<>(earnedPayoutCalculationService.periodKeysFor(anyRow.getTimePeriod()));
            List<Integer> overlap = periodMonthKeys.stream()
                    .filter(context.requestedPeriodKeys()::contains)
                    .toList();
                    
            if (overlap.isEmpty()) {
                continue;
            }

            // STEP 2: Aggregate Purchases (PurchaseAggregationService)
            PurchaseTotals totals = purchaseAggregationService.sumTotals(overlap, context);
            BigDecimal achieved = totals.netValue() != null ? totals.netValue() : BigDecimal.ZERO;
            long achievedQty = totals.netQty();

            // STEP 3: Calculate Earned Payout (EarnedPayoutCalculationService)
            MutableJbpLineItem lineItem = earnedPayoutCalculationService.evaluateLadder(
                    ladder, rowByCell, ladders, achieved, achievedQty, context.requestedPeriodKeys());
                    
            if (lineItem != null) {
                mutableItems.add(lineItem);
            }
        }

        // Apply Upward Contagion
        earnedPayoutCalculationService.applyUpwardContagion(mutableItems);

        List<CommercialPayoutLineItem> lineItems = mutableItems.stream()
                .map(MutableJbpLineItem::toDto)
                .toList();

        if (lineItems.isEmpty()) {
            notes.add("No JBP periods overlap the requested periods.");
        } else {
            if (allRows.stream().anyMatch(row -> row.getParentTimePeriod() != null)) {
                notes.add("Totals include all evaluated periods (both master and sub-periods).");
            }
            if (lineItems.stream().anyMatch(item -> STATUS_CHILD_QUALIFIER_MISSED.equals(item.statusReason()))) {
                notes.add(
                        "One or more parent periods were zeroed because a child period failed its qualifier gate.");
            }
        }

        BigDecimal totalPayout = lineItems.stream()
                .map(CommercialPayoutLineItem::payout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CommercialPayoutResponse(
                version.getId(),
                IncomeTypeNames.COMMERCIAL_CONTRACTS,
                version.getCommercialStructure(),
                totalPayout,
                lineItems.size(),
                lineItems,
                notes,
                context.rawData());
    }

    private CommercialPayoutResponse calculateFlatBaseline(CommercialCalculationContext context, List<String> notes) {
        AgreementVersion version = context.version();
        List<CommercialPayoutLineItem> lineItems = version.getCommercialStructure() == CommercialStructure.FLAT
                ? flatBaselinePayoutCalculator.calculateIntervals(SEGMENT_FLAT, context)
                : List.of();
        if (lineItems.isEmpty()) {
            notes.add("No JBP matrix configured and no flat baseline overlaps the requested periods.");
        }
        BigDecimal totalPayout = lineItems.stream()
                .map(CommercialPayoutLineItem::payout)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CommercialPayoutResponse(
                version.getId(),
                IncomeTypeNames.COMMERCIAL_CONTRACTS,
                version.getCommercialStructure(),
                totalPayout,
                lineItems.size(),
                lineItems,
                notes,
                context.rawData());
    }
}
