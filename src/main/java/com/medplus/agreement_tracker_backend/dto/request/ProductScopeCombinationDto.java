package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProductScopeCombinationDto(
        @NotNull(message = "Manufacturer id is required") Long manufacturerId,
        @Valid List<RuleDTO> divisionRules,
        @Valid List<ProductRuleDTO> productRules
) {}
