package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalProductItem {
    @JsonProperty("productId")
    private String productId;

    @JsonProperty("productName")
    private String productName;

    @JsonProperty("name")
    private String name;

    private Long manufacturerId;

    @JsonProperty("manufacturerName")
    private String manufacturerName;

    @JsonProperty("manufacturer")
    private String manufacturer;

    @JsonProperty("divisionId")
    private Long divisionId;

    @JsonProperty("manufacturerDivisionId")
    private Long manufacturerDivisionId;

    @JsonProperty("divisionName")
    private String divisionName;

    @JsonProperty("manufactureDivisionName")
    private String manufactureDivisionName;

    @JsonProperty("categoryId")
    private Integer categoryId;

    @JsonProperty("invoiceCategoryName")
    private String invoiceCategoryName;

    private BigDecimal mrp;
    private BigDecimal cp;
    private String status;

    public String resolvedProductName() {
        if (productName != null && !productName.isBlank()) {
            return productName;
        }
        return name;
    }

    public String resolvedManufacturerName() {
        if (manufacturerName != null && !manufacturerName.isBlank()) {
            return manufacturerName;
        }
        return manufacturer;
    }

    public Long resolvedDivisionId() {
        return manufacturerDivisionId != null ? manufacturerDivisionId : divisionId;
    }

    public String resolvedDivisionName() {
        if (divisionName != null && !divisionName.isBlank()) {
            return divisionName;
        }
        return manufactureDivisionName;
    }

    public String resolvedL3Category() {
        if (invoiceCategoryName != null && !invoiceCategoryName.isBlank()) {
            return invoiceCategoryName;
        }
        if (categoryId != null) {
            return String.valueOf(categoryId);
        }
        return resolvedDivisionName();
    }
}
