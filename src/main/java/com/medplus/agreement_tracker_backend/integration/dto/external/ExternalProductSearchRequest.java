package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medplus.agreement_tracker_backend.enums.ProductMasterStatusEnum;
import com.medplus.agreement_tracker_backend.enums.ProductTypeEnum;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
public class ExternalProductSearchRequest {
    private List<Long> manufacturerIdList;
    private List<Long> manufacturerDivisionId;
    private String searchKey;
    private Set<String> productIdList;

    @JsonProperty("isRunOnDb")
    private boolean isRunOnDb;

    @JsonProperty("isDetailsRequired")
    private boolean isDetailsRequired;

    @JsonProperty("manufacturerDivisionNotIn")
    private boolean manufacturerDivisionNotIn;

    @JsonProperty("productNotIn")
    private boolean productNotIn;

    @JsonProperty("manufacturerNotIn")
    private boolean manufacturerNotIn;

    private List<ProductTypeEnum> productTypes;
    private ProductMasterStatusEnum productStatus;

    @JsonProperty("requiredColumns")
    private List<ProductFieldsEnum> requiredColumns;

    private Integer minLimit;
    private Integer noOfRow;
}
