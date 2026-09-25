package com.medplus.agreement_tracker_backend.dto.response;

import java.math.BigDecimal;

public record VendorProductAggregateDto(
        Long supplierId,
        String productId,
        long totalNetQty,
        BigDecimal totalNetValue
) {
}
