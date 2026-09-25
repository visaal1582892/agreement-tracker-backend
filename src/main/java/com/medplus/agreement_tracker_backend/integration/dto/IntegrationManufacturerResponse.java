package com.medplus.agreement_tracker_backend.integration.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrationManufacturerResponse {
    private Long id;
    private String manufacturerName;
    private String status;
}
