package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalProductsPayload {
    @JsonProperty("products")
    private List<ExternalProductItem> products = new ArrayList<>();

    @JsonProperty("productList")
    private List<ExternalProductItem> productList = new ArrayList<>();

    @JsonProperty("totalCount")
    private Long totalCount;

    @JsonProperty("totalElements")
    private Long totalElements;

    @JsonProperty("noOfRecords")
    private Long noOfRecords;

    public List<ExternalProductItem> resolvedProducts() {
        if (productList != null && !productList.isEmpty()) {
            return productList;
        }
        return products != null ? products : List.of();
    }

    public long resolvedTotalElements(int pageSize, int returnedSize, int page) {
        if (totalElements != null && totalElements > 0) {
            return totalElements;
        }
        if (totalCount != null && totalCount > 0) {
            return totalCount;
        }
        if (noOfRecords != null && noOfRecords > 0) {
            return noOfRecords;
        }
        if (returnedSize < pageSize) {
            return (long) page * pageSize + returnedSize;
        }
        // Full page but API gave no total — signal unknown so UI can enable next page
        return -1L;
    }
}
