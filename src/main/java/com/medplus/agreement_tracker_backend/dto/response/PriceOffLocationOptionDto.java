package com.medplus.agreement_tracker_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PriceOffLocationOptionDto(
        String code,
        String name,
        @JsonProperty("isActive") boolean isActive
) {}
