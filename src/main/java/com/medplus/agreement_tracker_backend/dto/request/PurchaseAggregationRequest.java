package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Purchase-scope filters for commercial payout / aggregation.
 * Periods are required for purchase-driven income types (CC, Data Fee, Ad-Hoc)
 * and optional for Asset Rentals (store schedule only).
 */
public record PurchaseAggregationRequest(
        @Valid
        List<PurchaseAggregationPeriod> periods,

        List<String> productIds,

        List<Long> supplierIds,

        List<String> stateCodes,

        List<String> cityCodes
) {
}
