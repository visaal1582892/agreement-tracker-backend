package com.medplus.agreement_tracker_backend.dto.request;

import java.util.List;

public record CreateAgreementRequest(
        Long agreementGroupId,

        String newAgreementGroupName,

        List<Long> vendorIds,

        List<VendorSnapshotPayload> vendors,

        ProductRulesPayload productRules,

        List<DraftAgreementItemRequest> agreements
) {}
