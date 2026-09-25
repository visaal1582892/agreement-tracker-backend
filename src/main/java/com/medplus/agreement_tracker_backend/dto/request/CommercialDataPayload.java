package com.medplus.agreement_tracker_backend.dto.request;

import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;

import java.util.List;

/**
 * Optional nested commercial children for one-shot edit/renew submit.
 * Null/empty subtree → deep-copy that subtree from source version.
 */
public record CommercialDataPayload(
        List<Object> slabs,
        JbpStagedWorkbookDto jbp,
        List<StoreMappingSubmitDto> storeMappings,
        /** When set with jbp sheets, rebuild configs from this blueprint (client config ids). */
        JbpWorkbookRequest jbpBlueprint
) {
    public record StoreMappingSubmitDto(
            String storeId,
            String name,
            String address,
            Integer pinCode,
            String region1,
            String region2,
            String region3,
            boolean isCustom
    ) {}
}
