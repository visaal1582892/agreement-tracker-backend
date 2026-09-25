package com.medplus.agreement_tracker_backend.dto.request;

import java.util.List;

import jakarta.validation.Valid;

public record ProductRulesPayload(
        @Valid List<ProductScopeCombinationDto> combinations
) {}
