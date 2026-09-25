package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Stateless JBP template generation for Edit/Renew: dates + configuration blueprint, no DB writes.
 */
public record JbpStatelessPreviewRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate expiryDate,
        @NotNull @Valid JbpWorkbookRequest workbook
) {
}
