package com.medplus.agreement_tracker_backend.dto.common;

/**
 * Unified location DTO used in both request (DraftDetailsPayload) and response (AgreementVersionResponse).
 * Carries all three tiers of location data; unused tiers will be null depending on locationType.
 *
 * locationType: "COUNTRY" | "STATE" | "CITY"
 */
public record AgreementLocationDto(
        String locationType,

        String countryCode,
        String countryName,
        String countrySubName,

        String stateCode,
        String stateName,
        String stateSubName,

        String cityCode,
        String cityName,
        String citySubName
) {}
