package com.medplus.agreement_tracker_backend.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreLocationDetails {
    private String storeId;
    private String name;
    private String region1;
    private String region2;
    private String region3;
    private Integer pinCode;
    private String address;
}
