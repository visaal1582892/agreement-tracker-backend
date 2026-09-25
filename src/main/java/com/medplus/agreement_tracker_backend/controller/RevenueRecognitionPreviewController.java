package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.RevenueRecognitionPreviewResponse;
import com.medplus.agreement_tracker_backend.service.RevenueRecognitionPreviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.COMMERCIAL_PAYOUT_CALCULATE;

@RestController
@RequestMapping("/agreement-versions/{agreementVersionId}/revenue-recognition-preview")
@RequiredArgsConstructor
public class RevenueRecognitionPreviewController {

    private final RevenueRecognitionPreviewService previewService;

    @PostMapping
    @PreAuthorize(COMMERCIAL_PAYOUT_CALCULATE)
    public ResponseEntity<RevenueRecognitionPreviewResponse> preview(
            @PathVariable Long agreementVersionId,
            @Valid @RequestBody PurchaseAggregationRequest request) {
        return ResponseEntity.ok(previewService.preview(agreementVersionId, request));
    }
}
