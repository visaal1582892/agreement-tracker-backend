package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.enums.CommercialStructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CommercialPayoutResponse(
        Long agreementVersionId,
        String incomeType,
        CommercialStructure commercialStructure,
        BigDecimal totalPayout,
        int lineItemCount,
        List<CommercialPayoutLineItem> lineItems,
        List<String> notes,
        PurchaseAggregationResponse rawAggregation,
        Map<String, Object> breakdown
) {
    public CommercialPayoutResponse(
            Long agreementVersionId,
            String incomeType,
            CommercialStructure commercialStructure,
            BigDecimal totalPayout,
            int lineItemCount,
            List<CommercialPayoutLineItem> lineItems,
            List<String> notes,
            PurchaseAggregationResponse rawAggregation) {
        this(
                agreementVersionId,
                incomeType,
                commercialStructure,
                totalPayout,
                lineItemCount,
                lineItems,
                notes,
                rawAggregation,
                null);
    }
}
