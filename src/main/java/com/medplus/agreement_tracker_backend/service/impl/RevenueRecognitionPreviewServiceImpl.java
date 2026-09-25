package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutLineItem;
import com.medplus.agreement_tracker_backend.dto.response.RevenueRecognitionMonthDto;
import com.medplus.agreement_tracker_backend.dto.response.RevenueRecognitionPreviewResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementJbpCommercialPeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.exception.ResourceNotFoundException;
import com.medplus.agreement_tracker_backend.repository.AgreementJbpCommercialPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.CommercialCalculationDao.PurchaseTotals;
import com.medplus.agreement_tracker_backend.service.RevenueRecognitionPreviewService;
import com.medplus.agreement_tracker_backend.service.commercial.CommercialCalculationContext;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.EarnedPayoutCalculationService;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.MutableJbpLineItem;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.PaymentAllocationService;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.PeriodDetectionService;
import com.medplus.agreement_tracker_backend.service.commercial.jbp.PurchaseAggregationService;
import com.medplus.agreement_tracker_backend.repository.AgreementTimePeriodRepository;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RevenueRecognitionPreviewServiceImpl implements RevenueRecognitionPreviewService {

    private final AgreementVersionRepository versionRepository;
    private final AgreementJbpCommercialPeriodRepository jbpCommercialPeriodRepository;
    private final PeriodDetectionService periodDetectionService;
    private final PurchaseAggregationService purchaseAggregationService;
    private final EarnedPayoutCalculationService earnedPayoutCalculationService;
    private final PaymentAllocationService paymentAllocationService;
    private final AgreementTimePeriodRepository agreementTimePeriodRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public RevenueRecognitionPreviewResponse preview(Long agreementVersionId, PurchaseAggregationRequest request) {
        AgreementVersion version = versionRepository.findById(agreementVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AgreementVersion not found"));

        List<AgreementJbpCommercialPeriod> allRows =
                jbpCommercialPeriodRepository.findHydrationRowsByAgreementVersionId(version.getId());

        List<RevenueRecognitionMonthDto> monthlyPreviews = new ArrayList<>();

        if (allRows.isEmpty()) {
            return new RevenueRecognitionPreviewResponse(version.getId(), monthlyPreviews);
        }

        Map<String, AgreementJbpCommercialPeriod> rowByCell = periodDetectionService.indexByCell(allRows);
        Map<String, List<AgreementJbpCommercialPeriod>> ladders = periodDetectionService.groupByConfigAndPeriod(allRows);

        LocalDate start = version.getStartDate();
        LocalDate expiry = version.getExpiryDate() != null ? version.getExpiryDate() : LocalDate.now();

        YearMonth currentMonth = YearMonth.from(start);
        YearMonth endMonth = YearMonth.from(expiry);

        CommercialCalculationContext baseContext = new CommercialCalculationContext(
                version,
                version.getCalculationBasis(),
                null,
                request.productIds(),
                request.supplierIds(),
                request.stateCodes(),
                request.cityCodes(),
                null
        );

        while (!currentMonth.isAfter(endMonth)) {
            Integer currentMonthKey = currentMonth.getYear() * 100 + currentMonth.getMonthValue();
            
            List<String> triggeredPeriods = new ArrayList<>();
            BigDecimal earnedPayout = BigDecimal.ZERO;
            Map<String, List<CommercialPayoutLineItem>> breakdownByPeriod = new HashMap<>();

            for (List<AgreementJbpCommercialPeriod> ladder : ladders.values()) {
                AgreementJbpCommercialPeriod anyRow = ladder.get(0);
                AgreementTimePeriod timePeriod = anyRow.getTimePeriod();
                
                List<Integer> periodMonthKeys = earnedPayoutCalculationService.periodKeysFor(timePeriod);
                if (periodMonthKeys.isEmpty()) continue;

                Integer lastMonthInPeriod = periodMonthKeys.get(periodMonthKeys.size() - 1);
                
                if (currentMonthKey.equals(lastMonthInPeriod)) {
                    // Triggered!
                    String periodName = timePeriod.getName();
                    triggeredPeriods.add(periodName);

                    // Lookback window for this period is its periodMonthKeys up to now
                    List<Integer> overlapKeys = new ArrayList<>(periodMonthKeys);
                    
                    PurchaseTotals totals = purchaseAggregationService.sumTotals(overlapKeys, baseContext);
                    BigDecimal achieved = totals.netValue() != null ? totals.netValue() : BigDecimal.ZERO;
                    long achievedQty = totals.netQty();

                    MutableJbpLineItem lineItem = earnedPayoutCalculationService.evaluateLadder(
                            ladder, rowByCell, ladders, achieved, achievedQty, overlapKeys);

                    if (lineItem != null) {
                        CommercialPayoutLineItem dtoItem = lineItem.toDto();
                        earnedPayout = earnedPayout.add(dtoItem.payout());
                        breakdownByPeriod.computeIfAbsent(periodName, k -> new ArrayList<>()).add(dtoItem);
                    }
                }
            }

            monthlyPreviews.add(RevenueRecognitionMonthDto.builder()
                    .calendarMonth(String.format("%04d-%02d", currentMonth.getYear(), currentMonth.getMonthValue()))
                    .triggeredPeriods(triggeredPeriods)
                    .earnedPayout(earnedPayout)
                    .breakdownByPeriod(breakdownByPeriod)
                    .build());

            currentMonth = currentMonth.plusMonths(1);
        }

        PayoutFrequency paymentFrequency = null;
        List<AgreementTimePeriod> allPeriodsForFrequency = null;

        if (!allRows.isEmpty()) {
            com.medplus.agreement_tracker_backend.entity.AgreementJbpConfiguration config = allRows.get(0).getJbpConfiguration();
            if (config.getPaymentIntervals() != null && !config.getPaymentIntervals().isEmpty()) {
                String freqStr = config.getPaymentIntervals().get(0);
                try {
                    paymentFrequency = PayoutFrequency.valueOf(freqStr);
                    allPeriodsForFrequency = agreementTimePeriodRepository.findByPeriodFrequency(paymentFrequency);
                } catch (IllegalArgumentException e) {
                    // Invalid frequency
                }
            }
        }

        paymentAllocationService.allocatePayments(monthlyPreviews, paymentFrequency, allPeriodsForFrequency);

        return new RevenueRecognitionPreviewResponse(version.getId(), monthlyPreviews);
    }
}
