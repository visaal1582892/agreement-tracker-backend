package com.medplus.agreement_tracker_backend.integration.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class IntegrationProductResponse {
    private String id;
    private String productName;
    private Long manufacturerId;
    private String manufacturerName;
    private Long divisionId;
    private String divisionName;
    private Integer categoryId;
    private String l3Category;
    private BigDecimal mrp;
    private BigDecimal cp;
    private String status;
}
