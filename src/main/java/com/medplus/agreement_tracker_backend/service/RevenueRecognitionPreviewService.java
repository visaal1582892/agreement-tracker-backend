package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.RevenueRecognitionPreviewResponse;

public interface RevenueRecognitionPreviewService {
    RevenueRecognitionPreviewResponse preview(Long agreementVersionId, PurchaseAggregationRequest request);
}
