package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalManufacturerDivisionsPayload {
    private List<ExternalManufacturerWithDivisions> manufacturers = new ArrayList<>();
}
