package com.medplus.agreement_tracker_backend.dto.common;

public record PartnerLocationItemDto(
        String code,
        String name,
        String stateCode,
        String stateName
) {
    public PartnerLocationItemDto(String code, String name) {
        this(code, name, null, null);
    }
}
