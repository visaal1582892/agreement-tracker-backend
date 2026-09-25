package com.medplus.agreement_tracker_backend.integration.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IntegrationDivisionsRequest {
    @NotEmpty(message = "At least one manufacturer id is required")
    private List<Long> manufacturerIds;
    private String searchKey;
    private Integer page;
    private Integer size;
    private List<String> pinnedDivisionIds;
}
