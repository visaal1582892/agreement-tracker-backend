package com.medplus.agreement_tracker_backend.dto.request;

import java.util.List;

public record EditAgreementRequest(
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
