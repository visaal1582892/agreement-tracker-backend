package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutResponse;

public interface CommercialPayoutService {

    CommercialPayoutResponse calculatePayouts(Long agreementVersionId, PurchaseAggregationRequest request);
}
