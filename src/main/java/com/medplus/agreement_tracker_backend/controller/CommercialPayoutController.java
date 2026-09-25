package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.CommercialPayoutResponse;
import com.medplus.agreement_tracker_backend.service.CommercialPayoutService;
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
@RequestMapping("/agreement-versions/{agreementVersionId}/commercial-payouts")
@RequiredArgsConstructor
public class CommercialPayoutController {

    private final CommercialPayoutService commercialPayoutService;

    @PostMapping
    @PreAuthorize(COMMERCIAL_PAYOUT_CALCULATE)
    public ResponseEntity<CommercialPayoutResponse> calculatePayouts(
            @PathVariable Long agreementVersionId,
            @Valid @RequestBody PurchaseAggregationRequest request) {
        return ResponseEntity.ok(commercialPayoutService.calculatePayouts(agreementVersionId, request));
    }
}
