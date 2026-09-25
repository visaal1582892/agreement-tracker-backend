package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Contract date window for Edit/Renew JBP preview (periods / template) without a DRAFT version.
 */
public record JbpDateWindowRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate expiryDate
) {
}
