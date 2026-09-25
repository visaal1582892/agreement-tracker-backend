package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One-shot Edit / Renew submit payload. No DRAFT row is created;
 * the new version is written as PENDING_APPROVAL in a single transaction.
 */
public record AgreementRevisionSubmitRequest(
        @NotNull(message = "baseVersionNumber is required")
        Integer baseVersionNumber,

        @NotBlank(message = "Comments are required for edit/renew submission")
        String comments,

        String agreementName,
        List<Long> vendorIds,
        List<VendorSnapshotPayload> vendors,
        @Valid ProductRulesPayload productRules,
        @Valid DraftDetailsPayload details,
        @Valid DraftCommercialsPayload commercials,
        @Valid DraftAssetPayload asset,
        @Valid CommercialDataPayload commercialData
) {}
