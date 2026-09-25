package com.medplus.agreement_tracker_backend.dto.response;

import java.math.BigDecimal;

public record MonthlyCalculationDto(
        Long supplierId,
        String productId,
        Integer periodMonth,
        long totalNetQty,
        BigDecimal totalNetValue
) {
}
