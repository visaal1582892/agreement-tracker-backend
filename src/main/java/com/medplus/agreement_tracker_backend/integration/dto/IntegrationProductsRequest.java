package com.medplus.agreement_tracker_backend.integration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IntegrationProductsRequest {
    private String searchKey;
    private List<Long> manufacturerIds;
    private List<Long> divisionIds;
    private List<Long> excludeDivisionIds;
    private Integer page;
    private Integer size;
    private List<String> pinnedProductIds;
    private List<String> excludeProductIds;
}
