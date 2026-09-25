package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.PurchaseAggregationResponse;

public interface CommercialPurchaseAggregationService {

    PurchaseAggregationResponse aggregatePurchases(Long agreementVersionId, PurchaseAggregationRequest request);
}
