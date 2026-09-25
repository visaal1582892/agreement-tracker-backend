package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medplus.agreement_tracker_backend.enums.ProductMasterStatusEnum;
import com.medplus.agreement_tracker_backend.enums.ProductTypeEnum;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ExternalGetManufacturerRequest {
    private List<Long> manufacturerIds;
    private ProductMasterStatusEnum manufacturerStatus;

    @JsonProperty("isDivisionRequired")
    private boolean isDivisionRequired;
}
