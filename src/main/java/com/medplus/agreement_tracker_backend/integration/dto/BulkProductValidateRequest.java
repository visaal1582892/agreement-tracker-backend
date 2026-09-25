package com.medplus.agreement_tracker_backend.integration.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BulkProductValidateRequest {
    @NotEmpty
    @Size(max = 3000)
    private List<String> productIds;
}
