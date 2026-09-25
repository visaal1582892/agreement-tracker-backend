package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.request.ProductRulesPayload;
import com.medplus.agreement_tracker_backend.dto.response.ProductScopeCountResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.service.impl.AgreementProductScopeComputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agreements/product-scope")
@RequiredArgsConstructor
public class AgreementProductScopeController {

    private final AgreementProductScopeComputeService agreementProductScopeComputeService;
    private final AgreementComputedProductRepository computedProductRepository;

    @PostMapping("/count")
    @PreAuthorize("hasAnyAuthority('AGREEMENT_CREATE', 'AGREEMENT_EDIT', 'AGREEMENT_VIEW', 'AGREEMENT_VIEW_ALL')")
    public ResponseEntity<ProductScopeCountResponse> countScopedProducts(
            @Valid @RequestBody ProductRulesPayload request) {
        long count = agreementProductScopeComputeService.countScopedProducts(
                request.combinations());
        return ResponseEntity.ok(new ProductScopeCountResponse(count));
    }
}
