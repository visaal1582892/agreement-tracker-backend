package com.medplus.agreement_tracker_backend.integration.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LocationOptionResponse {
    private String name;
    private String code;
}
