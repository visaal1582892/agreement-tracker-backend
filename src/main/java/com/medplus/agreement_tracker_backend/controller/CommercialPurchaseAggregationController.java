package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.request.PurchaseAggregationRequest;
import com.medplus.agreement_tracker_backend.dto.response.PurchaseAggregationResponse;
import com.medplus.agreement_tracker_backend.service.CommercialPurchaseAggregationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.AGREEMENT_VIEW;

@RestController
@RequestMapping("/agreement-versions/{agreementVersionId}/purchase-aggregation")
@RequiredArgsConstructor
public class CommercialPurchaseAggregationController {

    private final CommercialPurchaseAggregationService commercialPurchaseAggregationService;

    @PostMapping
    @PreAuthorize(AGREEMENT_VIEW)
    public ResponseEntity<PurchaseAggregationResponse> aggregatePurchases(
            @PathVariable Long agreementVersionId,
            @Valid @RequestBody PurchaseAggregationRequest request) {
        return ResponseEntity.ok(commercialPurchaseAggregationService.aggregatePurchases(agreementVersionId, request));
    }
}
