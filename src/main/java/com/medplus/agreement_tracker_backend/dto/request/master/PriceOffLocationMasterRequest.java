package com.medplus.agreement_tracker_backend.dto.request.master;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PriceOffLocationMasterRequest {

    @NotBlank(message = "Location name is required")
    @Size(max = 255, message = "Location name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Location code is required")
    @Size(max = 50, message = "Location code must not exceed 50 characters")
    private String code;

    private Boolean isActive;
}
