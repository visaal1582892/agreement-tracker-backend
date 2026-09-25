package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.enums.CalculationBasis;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseAggregationResponse(
        Long agreementVersionId,
        CalculationBasis calculationBasis,
        String dateColumnUsed,
        int rowCount,
        long totalNetQty,
        BigDecimal totalNetValue,
        List<VendorProductAggregateDto> rows
) {
}
