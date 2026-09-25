package com.medplus.agreement_tracker_backend.integration.dto.external;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PartnerSearchRequest {
    private String searchKey;
    private String limit;
    private String tenantId;
    private java.util.List<String> stateCodes;
}
