package com.medplus.agreement_tracker_backend.dto.response;

import java.math.BigDecimal;

public record PriceOffUploadSummaryDto(
        int successfullyParsedRows,
        int errorRows,
        BigDecimal totalCampaignQuantity,
        BigDecimal totalExpectedCreditNote
) {}
