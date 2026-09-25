package com.medplus.agreement_tracker_backend.service.commercial;

import com.medplus.agreement_tracker_backend.constants.IncomeTypeNames;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutLineItem;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementSlab;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.CapUnit;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.repository.AgreementSlabRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ad-Hoc Activities: a one-time payout.
 *
 * <p>QUANTITY mode (unchanged): per-unit rate from version {@code commercialValue},
 * eligible qty clamped to max quantity cap.
 *
 * <p>RUPEES mode (Directive 3): highest qualifying tier where {@code purchase >= min_cap}
 * (max min_cap wins). Payout =
 * {@code MIN(purchase, max_cap) * (slab.commercialValue / 100)}.
 */
@Component
@RequiredArgsConstructor
public class AdhocCalculationStrategy implements CommercialCalculationStrategy {

    private static final String SEGMENT_TYPE = "ADHOC";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AgreementSlabRepository slabRepository;

    @Override
    public String supportedIncomeType() {
        return IncomeTypeNames.AD_HOC_ACTIVITIES;
    }

    @Override
    public CommercialPayoutResponse calculatePayout(CommercialCalculationContext context) {
        AgreementVersion version = context.version();
        List<AgreementSlab> slabs = slabRepository.findByAgreementVersionIdOrderByMinCapAsc(version.getId());
        boolean quantityMode = isQuantityMode(version, slabs);

        CommercialPayoutLineItem lineItem;
        Map<String, Object> breakdown;
        if (quantityMode) {
            lineItem = calculatePerUnit(context, slabs);
            breakdown = null;
        } else {
            ValueCalcResult valueResult = calculateValue(context, slabs);
            lineItem = valueResult.lineItem();
            breakdown = valueResult.breakdown();
        }

        List<String> notes = new ArrayList<>();
        if (!lineItem.qualifierMet()) {
            notes.add("Ad-hoc eligibility not met; payout is zero.");
        }
        if (lineItem.capped()) {
            notes.add("Ad-hoc payout was capped by a configured guardrail.");
        }

        return new CommercialPayoutResponse(
                version.getId(),
                IncomeTypeNames.AD_HOC_ACTIVITIES,
                version.getCommercialStructure(),
                lineItem.payout(),
                1,
                List.of(lineItem),
                notes,
                context.rawData(),
                breakdown);
    }

    private boolean isQuantityMode(AgreementVersion version, List<AgreementSlab> slabs) {
        if (!slabs.isEmpty()) {
            return slabs.get(0).getCapUnit() == CapUnit.QUANTITY;
        }
        return version.getQuantityCap() != null;
    }

    private CommercialPayoutLineItem calculatePerUnit(CommercialCalculationContext context, List<AgreementSlab> slabs) {
        AgreementVersion version = context.version();
        BigDecimal perUnitRate = nullSafe(version.getCommercialValue());
        BigDecimal actualQty = BigDecimal.valueOf(context.rawData().totalNetQty());

        BigDecimal qtyMin = extremum(slabs, CapUnit.QUANTITY, true);
        BigDecimal qtyMax = extremum(slabs, CapUnit.QUANTITY, false);
        BigDecimal maxQty = resolveQuantityCeiling(qtyMax, version.getQuantityCap());

        if (qtyMin != null && actualQty.compareTo(qtyMin) < 0) {
            return new CommercialPayoutLineItem(
                    SEGMENT_TYPE, "Ad-Hoc Activity", null, null, context.rawData().totalNetValue(),
                    context.rawData().totalNetQty(), BigDecimal.ZERO, false, false,
                    "Net quantity " + actualQty.toPlainString() + " below minimum quantity cap " + qtyMin.toPlainString(),
                    "Below Minimum Quantity", null, null, PayoutFrequency.ONE_TIME, List.of());
        }

        BigDecimal eligibleQty = actualQty;
        boolean capped = false;
        if (maxQty != null && eligibleQty.compareTo(maxQty) > 0) {
            eligibleQty = maxQty;
            capped = true;
        }

        BigDecimal payout = perUnitRate.multiply(eligibleQty).setScale(2, RoundingMode.HALF_UP);
        String detail = "Per-unit rate " + perUnitRate.stripTrailingZeros().toPlainString()
                + " x eligible qty " + eligibleQty.toPlainString()
                + (capped ? " (clamped to max qty " + maxQty.toPlainString() + ")" : "");

        return new CommercialPayoutLineItem(
                SEGMENT_TYPE, "Ad-Hoc Activity", null, null, context.rawData().totalNetValue(),
                context.rawData().totalNetQty(), payout, true, capped, detail,
                "Payable", null, null, PayoutFrequency.ONE_TIME, List.of());
    }

    /**
     * Highest qualifying RUPEES tier (purchase &gt;= min_cap, max min_cap wins).
     * Formula: MIN(purchase, max_cap) * (slab% / 100).
     */
    private ValueCalcResult calculateValue(CommercialCalculationContext context, List<AgreementSlab> slabs) {
        BigDecimal totalNetValue = nullSafe(context.rawData().totalNetValue());
        long totalNetQty = context.rawData().totalNetQty();

        AgreementSlab matched = selectHighestQualifyingRupeeSlab(slabs, totalNetValue);
        if (matched == null) {
            Map<String, Object> breakdown = baseAdhocBreakdown(totalNetValue);
            breakdown.put("applicableSlab", null);
            breakdown.put("eligiblePurchase", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            breakdown.put("appliedPercentage", null);
            breakdown.put("totalPayout", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            breakdown.put("qualifierMet", false);

            CommercialPayoutLineItem lineItem = new CommercialPayoutLineItem(
                    SEGMENT_TYPE, "Ad-Hoc Activity", null, null, totalNetValue, totalNetQty,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), false, false,
                    "Purchase " + totalNetValue.toPlainString() + " does not meet any slab minimum",
                    "Below Minimum Cap", null, null, PayoutFrequency.ONE_TIME, List.of());
            return new ValueCalcResult(lineItem, breakdown);
        }

        BigDecimal maxCap = matched.getMaxCap();
        BigDecimal eligible = totalNetValue;
        boolean capped = false;
        if (maxCap != null && eligible.compareTo(maxCap) > 0) {
            eligible = maxCap;
            capped = true;
        }
        eligible = eligible.setScale(2, RoundingMode.HALF_UP);

        BigDecimal percentage = nullSafe(matched.getCommercialValue());
        BigDecimal payout = eligible.multiply(percentage)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        String detail = "MIN(purchase " + totalNetValue.toPlainString()
                + ", max_cap " + (maxCap != null ? maxCap.toPlainString() : "n/a") + ")"
                + " × " + percentage.stripTrailingZeros().toPlainString() + "%";

        Map<String, Object> breakdown = baseAdhocBreakdown(totalNetValue);
        Map<String, Object> slabInfo = new LinkedHashMap<>();
        slabInfo.put("minCap", matched.getMinCap());
        slabInfo.put("maxCap", matched.getMaxCap());
        slabInfo.put("commercialValue", matched.getCommercialValue());
        slabInfo.put("valueType", matched.getValueType() != null ? matched.getValueType().name() : null);
        breakdown.put("applicableSlab", slabInfo);
        breakdown.put("eligiblePurchase", eligible);
        breakdown.put("appliedPercentage", percentage);
        breakdown.put("capped", capped);
        breakdown.put("totalPayout", payout);
        breakdown.put("qualifierMet", true);

        CommercialPayoutLineItem lineItem = new CommercialPayoutLineItem(
                SEGMENT_TYPE, "Ad-Hoc Activity", null, null, totalNetValue, totalNetQty,
                payout, true, capped, detail, "Payable", null, null, PayoutFrequency.ONE_TIME, List.of());
        return new ValueCalcResult(lineItem, breakdown);
    }

    private AgreementSlab selectHighestQualifyingRupeeSlab(List<AgreementSlab> slabs, BigDecimal purchase) {
        AgreementSlab best = null;
        for (AgreementSlab slab : slabs) {
            if (slab.getCapUnit() != null && slab.getCapUnit() != CapUnit.RUPEES) {
                continue;
            }
            BigDecimal minCap = slab.getMinCap();
            if (minCap == null) {
                continue;
            }
            if (purchase.compareTo(minCap) < 0) {
                continue;
            }
            if (best == null || minCap.compareTo(best.getMinCap()) > 0) {
                best = slab;
            }
        }
        return best;
    }

    private Map<String, Object> baseAdhocBreakdown(BigDecimal totalPurchase) {
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("mode", "RUPEES_SLAB");
        breakdown.put("title", "Ad-Hoc Slab Payout");
        breakdown.put("totalActualPurchase", totalPurchase.setScale(2, RoundingMode.HALF_UP));
        return breakdown;
    }

    private BigDecimal resolveQuantityCeiling(BigDecimal qtyMax, BigDecimal versionQuantityCap) {
        if (qtyMax == null) {
            return versionQuantityCap;
        }
        if (versionQuantityCap == null) {
            return qtyMax;
        }
        return qtyMax.min(versionQuantityCap);
    }

    private BigDecimal extremum(List<AgreementSlab> slabs, CapUnit capUnit, boolean minimum) {
        BigDecimal result = null;
        for (AgreementSlab slab : slabs) {
            if (slab.getCapUnit() != capUnit) {
                continue;
            }
            BigDecimal candidate = minimum ? slab.getMinCap() : slab.getMaxCap();
            if (candidate == null) {
                continue;
            }
            if (result == null) {
                result = candidate;
            } else {
                result = minimum ? result.min(candidate) : result.max(candidate);
            }
        }
        return result;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private record ValueCalcResult(CommercialPayoutLineItem lineItem, Map<String, Object> breakdown) {
    }
}
