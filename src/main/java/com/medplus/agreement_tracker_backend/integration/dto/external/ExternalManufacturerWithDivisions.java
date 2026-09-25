package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalManufacturerWithDivisions {
    private Long manufacturerId;

    @JsonProperty("manufacturerName")
    private String manufacturerName;

    @JsonProperty("manfacturer")
    private String manfacturer;

    private String status;

    @JsonProperty("divisions")
    private List<ExternalDivisionItem> divisions = new ArrayList<>();

    @JsonProperty("manufacturerDivisions")
    private List<ExternalDivisionItem> manufacturerDivisions = new ArrayList<>();

    public List<ExternalDivisionItem> resolvedDivisions() {
        if (manufacturerDivisions != null && !manufacturerDivisions.isEmpty()) {
            return manufacturerDivisions;
        }
        return divisions != null ? divisions : List.of();
    }

    public String resolvedManufacturerName() {
        if (manufacturerName != null && !manufacturerName.isBlank()) {
            return manufacturerName;
        }
        return manfacturer;
    }
}
