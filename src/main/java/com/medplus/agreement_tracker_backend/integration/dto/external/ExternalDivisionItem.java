package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalDivisionItem {
    @JsonProperty("divisionId")
    private Long divisionId;

    @JsonProperty("manufacturerDivisionId")
    private Long manufacturerDivisionId;

    @JsonProperty("divisionName")
    private String divisionName;

    @JsonProperty("division")
    private String division;

    private String status;

    public Long resolvedDivisionId() {
        return manufacturerDivisionId != null ? manufacturerDivisionId : divisionId;
    }

    public String resolvedDivisionName() {
        if (divisionName != null && !divisionName.isBlank()) {
            return divisionName;
        }
        return division;
    }
}
