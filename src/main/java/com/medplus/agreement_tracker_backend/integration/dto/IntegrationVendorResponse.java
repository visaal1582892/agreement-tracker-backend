package com.medplus.agreement_tracker_backend.integration.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrationVendorResponse {
    private Long vendorId;
    private String vendorName;
    private String state;

    private Company company;

    @Getter
    @Builder
    public static class Company {
        private String state;
        private String city;
    }
}
