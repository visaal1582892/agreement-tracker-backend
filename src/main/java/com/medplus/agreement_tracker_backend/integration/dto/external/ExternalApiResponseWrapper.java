package com.medplus.agreement_tracker_backend.integration.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalApiResponseWrapper {
    private String responseStatus;
    private Object errors;
    private Object indexWiseErrors;
    private String response;
    private Boolean error;
}
