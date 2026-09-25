package com.medplus.agreement_tracker_backend.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreDetails {
    private String storeId;
    private String storeName;
    private String city;
    private String status;
}
