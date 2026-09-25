package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgreementStoreDto {
    @NotBlank(message = "Store ID is required")
    private String storeId;
    private String name;
    private String address;
    private Integer pinCode;
    private String region1; // Country
    private String region2; // State
    private String region3; // City
    private boolean isCustom;
}
