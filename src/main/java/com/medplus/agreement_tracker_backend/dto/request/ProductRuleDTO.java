package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductRuleDTO(
        @NotBlank(message = "Product rule id is required") String id,
        @NotNull(message = "Rule type is required") String ruleType,
        @NotNull(message = "Manufacturer id is required") Long manufacturerId
) {}
