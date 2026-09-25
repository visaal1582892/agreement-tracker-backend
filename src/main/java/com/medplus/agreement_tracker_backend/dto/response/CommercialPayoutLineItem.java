package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;

import java.math.BigDecimal;
import java.util.List;

public record CommercialPayoutLineItem(
        String segmentType,
        String label,
        Long timePeriodId,
        Integer slabTier,
        BigDecimal basisNetValue,
        long basisNetQty,
        BigDecimal payout,
        boolean qualifierMet,
        boolean capped,
        String detail,
        String statusReason,
        BigDecimal qualifierRequiredPercent,
        BigDecimal qualifierAchievedValue,
        PayoutFrequency periodFrequency,
        List<Integer> periodMonthKeys
) {
}
