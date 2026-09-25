package com.medplus.agreement_tracker_backend.dto.request;

public record VendorSnapshotPayload(
        Long vendorId,
        String vendorName,
        String state
) {}
