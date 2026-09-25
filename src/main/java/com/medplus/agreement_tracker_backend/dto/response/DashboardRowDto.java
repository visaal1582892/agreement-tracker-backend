package com.medplus.agreement_tracker_backend.dto.response;

import java.math.BigDecimal;

public record DashboardRowDto(
        Long id,
        String agreementName,
        String versionName,
        Long supplierId,
        Integer calendarYear,
        Integer calendarMonth,
        String triggeredFrequencies,
        BigDecimal earnedAmount,
        BigDecimal payableAmount,
        String paymentInterval
) {
}
