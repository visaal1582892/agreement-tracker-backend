package com.medplus.agreement_tracker_backend.dto.response;

public record AgreementStoreMappingResponse(
        Long mappingId,
        String storeId,
        String name,
        String address,
        Integer pinCode,
        String region1,
        String region2,
        String region3,
        boolean isCustom
) {}
