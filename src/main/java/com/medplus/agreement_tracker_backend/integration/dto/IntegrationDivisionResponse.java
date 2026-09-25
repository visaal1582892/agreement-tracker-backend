package com.medplus.agreement_tracker_backend.integration.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrationDivisionResponse {
    private Long id;
    private String divisionName;
    private Long manufacturerId;
    private String manufacturerName;
    private String status;
}
