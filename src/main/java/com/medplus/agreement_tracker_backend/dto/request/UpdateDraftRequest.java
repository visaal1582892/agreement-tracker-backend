package com.medplus.agreement_tracker_backend.dto.request;

import java.util.List;

public record UpdateDraftRequest(
        /** Ignored on update — name is server-generated via {@code regenerateAgreementName}. */
        String agreementName,
        List<Long> vendorIds,
        List<VendorSnapshotPayload> vendors,
        ProductRulesPayload productRules,
        DraftDetailsPayload details,
        DraftCommercialsPayload commercials,
        DraftAssetPayload asset,
        Boolean requiresReapproval,
        CommercialDataPayload commercialData
) {}
