package com.medplus.agreement_tracker_backend.service.commercial;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutLineItem;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.entity.AgreementAssetPayoutPeriod;
import com.medplus.agreement_tracker_backend.enums.CommercialStructure;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.repository.AgreementAssetPayoutPeriodRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementStoreMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asset Rentals payout:
 * <ul>
 *   <li>Flat one-time — configured {@code flatPayout}; no purchase aggregation.</li>
 *   <li>Per-store schedule — sum over periods of
 *       {@code mappedStores * payoutPerStore * periodMonths}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AssetCalculationStrategy implements CommercialCalculationStrategy {

    private static final String SEGMENT_FLAT = "ASSET_FLAT";
    private static final String SEGMENT_PER_STORE = "ASSET_PER_STORE";

    private final AgreementAssetPayoutPeriodRepository assetPayoutPeriodRepository;
    private final AgreementStoreMappingRepository storeMappingRepository;

    @Override
    public String supportedIncomeType() {
        return IncomeTypeNames.ASSET_RENTALS;
    }

    @Override
    public CommercialPayoutResponse calculatePayout(CommercialCalculationContext context) {
        AgreementVersion version = context.version();
        long mappedStores = storeMappingRepository.countByAgreementVersionId(version.getId());
        if (mappedStores <= 0) {
            throw new BusinessException("No store mappings found for this Asset Rentals agreement");
        }

        boolean isFlat = version.getCommercialStructure() == CommercialStructure.FLAT;
        BigDecimal flatPayout = isFlat && version.getCommercialValue() != null 
                ? version.getCommercialValue() 
                : BigDecimal.ZERO;

        List<AgreementAssetPayoutPeriod> periods =
                assetPayoutPeriodRepository.findByAgreementVersionIdOrderByPeriodMonthsAscIdAsc(version.getId());

        if (isFlat) {
            return buildFlatResponse(version, flatPayout, mappedStores);
        }
        if (periods.isEmpty()) {
            throw new BusinessException(
                    "Asset Rentals agreement has neither a flat payout nor a per-store schedule");
        }
        return buildPerStoreResponse(version, periods, mappedStores);
    }

    private CommercialPayoutResponse buildFlatResponse(
            AgreementVersion version,
            BigDecimal flatPayout,
            long mappedStores) {
        BigDecimal payout = flatPayout.setScale(2, RoundingMode.HALF_UP);
        CommercialPayoutLineItem lineItem = new CommercialPayoutLineItem(
                SEGMENT_FLAT,
                "Flat One-Time Payout",
                null,
                null,
                BigDecimal.ZERO,
                0L,
                payout,
                true,
                false,
                "Configured flat one-time asset payout",
                "Payable",
                null,
                null,
                null,
                List.of());

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("mode", "FLAT");
        breakdown.put("title", "Flat One-Time Payout");
        breakdown.put("flatPayout", payout);
        breakdown.put("mappedStores", mappedStores);
        breakdown.put("totalPayout", payout);

        return new CommercialPayoutResponse(
                version.getId(),
                IncomeTypeNames.ASSET_RENTALS,
                null,
                payout,
                1,
                List.of(lineItem),
                List.of(),
                null,
                breakdown);
    }

    private CommercialPayoutResponse buildPerStoreResponse(
            AgreementVersion version,
            List<AgreementAssetPayoutPeriod> periods,
            long mappedStores) {
        BigDecimal storeCountBd = BigDecimal.valueOf(mappedStores);
        BigDecimal totalPayout = BigDecimal.ZERO;
        int activeMonths = 0;
        List<Map<String, Object>> periodRows = new ArrayList<>();
        List<CommercialPayoutLineItem> lineItems = new ArrayList<>();

        for (AgreementAssetPayoutPeriod period : periods) {
            int months = period.getPeriodMonths() != null ? period.getPeriodMonths() : 0;
            BigDecimal perStore = period.getPayoutPerStore() != null
                    ? period.getPayoutPerStore()
                    : BigDecimal.ZERO;
            // Rate × months = total payable for one store in this schedule row.
            BigDecimal totalPayoutPerStore = perStore
                    .multiply(BigDecimal.valueOf(months))
                    .setScale(2, RoundingMode.HALF_UP);
            // Then × mapped stores = aggregated payout for this row.
            BigDecimal aggregatedPayout = storeCountBd
                    .multiply(totalPayoutPerStore)
                    .setScale(2, RoundingMode.HALF_UP);
            totalPayout = totalPayout.add(aggregatedPayout);
            activeMonths += months;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("periodMonths", months);
            row.put("payoutPerStore", perStore);
            row.put("totalPayoutPerStore", totalPayoutPerStore);
            row.put("aggregatedPayout", aggregatedPayout);
            row.put("rowPayout", aggregatedPayout);
            periodRows.add(row);

            lineItems.add(new CommercialPayoutLineItem(
                    SEGMENT_PER_STORE,
                    months + " month(s) @ " + perStore.toPlainString() + "/store",
                    null,
                    null,
                    BigDecimal.ZERO,
                    mappedStores,
                    aggregatedPayout,
                    true,
                    false,
                    "₹" + perStore.toPlainString() + "/store × " + months + " month(s) = ₹"
                            + totalPayoutPerStore.toPlainString() + "/store; × "
                            + mappedStores + " stores = ₹" + aggregatedPayout.toPlainString(),
                    "Payable",
                    null,
                    null,
                    null,
                    List.of()));
        }

        totalPayout = totalPayout.setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("mode", "PER_STORE");
        breakdown.put("title", "Payment Per Store");
        breakdown.put("mappedStores", mappedStores);
        breakdown.put("activeMonths", activeMonths);
        breakdown.put("periods", periodRows);
        breakdown.put("totalPayout", totalPayout);

        return new CommercialPayoutResponse(
                version.getId(),
                IncomeTypeNames.ASSET_RENTALS,
                null,
                totalPayout,
                lineItems.size(),
                lineItems,
                List.of(),
                null,
                breakdown);
    }
}
