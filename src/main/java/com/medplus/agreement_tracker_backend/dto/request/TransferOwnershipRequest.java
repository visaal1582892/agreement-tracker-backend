package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferOwnershipRequest(
        @NotNull(message = "New owner user id is required") Long newOwnerUserId,
        @Size(max = 1000)
        String comments
) {}
