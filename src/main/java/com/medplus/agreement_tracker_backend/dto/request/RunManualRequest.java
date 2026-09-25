package com.medplus.agreement_tracker_backend.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class RunManualRequest {
    private List<Integer> monthKeys;
    private Long supplierId;
    private Long agreementId;
}
