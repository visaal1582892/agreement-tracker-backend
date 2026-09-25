package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.dto.request.JbpConfigurationBlockDto;

import java.util.List;
import java.util.Map;

public record JbpStructureHydrationResponse(
        List<JbpConfigurationBlockDto> configurations,
        Map<String, JbpStagedWorkbookDto> stagedWorkbooks
) {
}
