package com.medplus.agreement_tracker_backend.service.commercial;

import com.medplus.agreement_tracker_backend.dto.response.PurchaseAggregationResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.CalculationBasis;

import java.util.List;

/**
 * Immutable input carried into a {@link CommercialCalculationStrategy}. Holds the resolved purchase
 * scope so strategies that need per-agreement-period purchase (JBP) query against the exact same
 * product/supplier/state/city scope that produced the grand-total {@code rawData}.
 */
public record CommercialCalculationContext(
        AgreementVersion version,
        CalculationBasis calculationBasis,
        List<Integer> requestedPeriodKeys,
        List<String> productIds,
        List<Long> supplierIds,
        List<String> stateCodes,
        List<String> cityCodes,
        PurchaseAggregationResponse rawData
) {
}
