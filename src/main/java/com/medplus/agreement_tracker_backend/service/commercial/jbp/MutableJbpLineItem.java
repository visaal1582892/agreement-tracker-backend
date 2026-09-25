package com.medplus.agreement_tracker_backend.service.commercial.jbp;

import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutLineItem;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Getter
public class MutableJbpLineItem {
    private static final String STATUS_CHILD_QUALIFIER_MISSED = "Child Qualifier Missed";

    private final String segmentType;
    private final String label;
    private final String periodName;
    private final Long timePeriodId;
    private final Long configId;
    private final PayoutFrequency periodFrequency;
    private final Set<Integer> periodMonthKeys;
    private final Integer slabTier;
    private final BigDecimal basisNetValue;
    private final long basisNetQty;
    private final BigDecimal qualifierRequiredPercent;
    private final BigDecimal qualifierAchievedValue;

    private BigDecimal payout;
    private boolean qualifierMet;
    private boolean capped;
    private String statusReason;
    private String detail;

    public MutableJbpLineItem(
            String segmentType,
            String label,
            String periodName,
            Long timePeriodId,
            Long configId,
            PayoutFrequency periodFrequency,
            Set<Integer> periodMonthKeys,
            Integer slabTier,
            BigDecimal basisNetValue,
            long basisNetQty,
            BigDecimal payout,
            boolean qualifierMet,
            boolean capped,
            String detail,
            String statusReason,
            BigDecimal qualifierRequiredPercent,
            BigDecimal qualifierAchievedValue) {
        this.segmentType = segmentType;
        this.label = label;
        this.periodName = periodName;
        this.timePeriodId = timePeriodId;
        this.configId = configId;
        this.periodFrequency = periodFrequency;
        this.periodMonthKeys = periodMonthKeys;
        this.slabTier = slabTier;
        this.basisNetValue = basisNetValue;
        this.basisNetQty = basisNetQty;
        this.payout = payout;
        this.qualifierMet = qualifierMet;
        this.capped = capped;
        this.detail = detail;
        this.statusReason = statusReason;
        this.qualifierRequiredPercent = qualifierRequiredPercent;
        this.qualifierAchievedValue = qualifierAchievedValue;
    }

    public boolean qualifierMet() {
        return qualifierMet;
    }

    public String statusReason() {
        return statusReason;
    }

    public Long configId() {
        return configId;
    }

    public Long timePeriodId() {
        return timePeriodId;
    }

    public String periodName() {
        return periodName;
    }

    public Set<Integer> periodMonthKeys() {
        return periodMonthKeys;
    }

    public void forceChildQualifierFailure(String childPeriodName) {
        qualifierMet = false;
        payout = BigDecimal.ZERO;
        statusReason = STATUS_CHILD_QUALIFIER_MISSED;
        capped = false;
        detail = "Failed: Child Qualifier Unmet (" + childPeriodName + ")";
    }

    public CommercialPayoutLineItem toDto() {
        return new CommercialPayoutLineItem(
                segmentType,
                label,
                timePeriodId,
                slabTier,
                basisNetValue,
                basisNetQty,
                payout,
                qualifierMet,
                capped,
                detail,
                statusReason,
                qualifierRequiredPercent,
                qualifierAchievedValue,
                periodFrequency,
                List.copyOf(periodMonthKeys));
    }
}
