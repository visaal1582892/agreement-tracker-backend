package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.request.ApprovalActionRequest;
import com.medplus.agreement_tracker_backend.dto.request.EditAgreementRequest;
import com.medplus.agreement_tracker_backend.dto.request.SubmitForApprovalRequest;
import com.medplus.agreement_tracker_backend.dto.request.TerminateAgreementRequest;
import com.medplus.agreement_tracker_backend.dto.request.TransferOwnershipRequest;
import com.medplus.agreement_tracker_backend.dto.request.UpdateDraftRequest;
import com.medplus.agreement_tracker_backend.dto.response.AgreementVersionResponse;
import com.medplus.agreement_tracker_backend.dto.response.ApprovalTimelineResponse;
import com.medplus.agreement_tracker_backend.dto.response.PagedResponse;
import com.medplus.agreement_tracker_backend.enums.RightCode;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;
import com.medplus.agreement_tracker_backend.service.AgreementService;
import com.medplus.agreement_tracker_backend.validation.DraftValidation;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementComputedProductSpecification;
import com.medplus.agreement_tracker_backend.entity.AgreementComputedProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.*;

@RestController
@RequestMapping("/agreement-versions")
@RequiredArgsConstructor
public class AgreementVersionController {

    private final AgreementService agreementService;
    private final AgreementComputedProductRepository computedProductRepository;

    @GetMapping("/{id}")
    @PreAuthorize(AGREEMENT_VIEW)
    public ResponseEntity<AgreementVersionResponse> getAgreementVersion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(agreementService.getAgreementVersionById(id, principal.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(DRAFT_EDIT)
    public ResponseEntity<AgreementVersionResponse> updateDraft(
            @PathVariable Long id,
            @Validated(DraftValidation.class) @RequestBody UpdateDraftRequest request,
            @RequestParam(defaultValue = "false") boolean validateStep1,
            @RequestParam(defaultValue = "false") boolean validateStep2,
            @RequestParam(defaultValue = "false") boolean validateCommercialStructure,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(agreementService.updateDraft(
                id, request, principal.getId(), validateStep1, validateStep2, validateCommercialStructure));
    }

    @DeleteMapping("/{id}/discard")
    @PreAuthorize(DRAFT_DELETE_V)
    public ResponseEntity<Void> discardDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        agreementService.discardDraftVersion(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/init-edit")
    @PreAuthorize(AGREEMENT_EDIT_ID)
    public ResponseEntity<AgreementVersionResponse> initEdit(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(agreementService.initEdit(id, principal.getId()));
    }

    @PostMapping("/{id}/init-renew")
    @PreAuthorize(AGREEMENT_RENEW)
    public ResponseEntity<AgreementVersionResponse> initRenew(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(agreementService.initRenew(id, principal.getId()));
    }

    @PostMapping("/{id}/init-revise")
    @PreAuthorize(AGREEMENT_REVISE)
    public ResponseEntity<AgreementVersionResponse> initRevise(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(agreementService.initRevise(id, principal.getId()));
    }

    @PostMapping("/{id}/clone")
    @PreAuthorize(AGREEMENT_CLONE)
    public ResponseEntity<AgreementVersionResponse> cloneAgreement(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agreementService.cloneAgreement(id, principal.getId()));
    }

    @PutMapping("/{id}/submit-edit")
    @PreAuthorize(AGREEMENT_SUBMIT_V)
    public ResponseEntity<AgreementVersionResponse> submitEdit(
            @PathVariable Long id,
            @RequestBody(required = false) SubmitForApprovalRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String comments = request != null ? request.comments() : null;
        return ResponseEntity.ok(agreementService.submitEdit(id, comments, principal.getId()));
    }

    @PutMapping("/{id}/submit-renew")
    @PreAuthorize(AGREEMENT_SUBMIT_V)
    public ResponseEntity<AgreementVersionResponse> submitRenew(
            @PathVariable Long id,
            @RequestBody(required = false) SubmitForApprovalRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String comments = request != null ? request.comments() : null;
        return ResponseEntity.ok(agreementService.submitRenew(id, comments, principal.getId()));
    }

    @PutMapping("/{id}/submit-revise")
    @PreAuthorize(AGREEMENT_SUBMIT_V)
    public ResponseEntity<AgreementVersionResponse> submitRevise(
            @PathVariable Long id,
            @RequestBody(required = false) SubmitForApprovalRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String comments = request != null ? request.comments() : null;
        return ResponseEntity.ok(agreementService.submitRevise(id, comments, principal.getId()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(AGREEMENT_APPROVE)
    public ResponseEntity<AgreementVersionResponse> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String remarks = request != null ? request.remarks() : null;
        return ResponseEntity.ok(agreementService.approve(id, remarks, principal.getId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(AGREEMENT_REJECT)
    public ResponseEntity<AgreementVersionResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(agreementService.reject(id, request.remarks(), principal.getId()));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize(AGREEMENT_TERMINATE_V)
    public ResponseEntity<AgreementVersionResponse> terminate(
            @PathVariable Long id,
            @Valid @RequestBody TerminateAgreementRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(agreementService.terminate(id, request, principal.getId()));
    }

    @GetMapping("/pending-approvals")
    @PreAuthorize(AGREEMENT_APPROVE)
    public ResponseEntity<PagedResponse<AgreementVersionResponse>> getPendingApprovals(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(PagedResponse.from(agreementService.getPendingApprovals(search, pageable)));
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize(AGREEMENT_VIEW)
    public ResponseEntity<List<ApprovalTimelineResponse>> getTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(agreementService.getApprovalTimeline(id));
    }

    @PutMapping("/{id}/transfer")
    @PreAuthorize("hasAnyAuthority('ADMIN_USERS', 'AGREEMENT_TRANSFER')")
    public ResponseEntity<AgreementVersionResponse> transferOwnership(
            @PathVariable Long id,
            @Valid @RequestBody TransferOwnershipRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.hasRight(RightCode.ADMIN_USERS.name());
        return ResponseEntity.ok(agreementService.transferOwnership(
                id, request.newOwnerUserId(), principal.getId(), isAdmin, request.comments()));
    }

    @GetMapping("/{id}/computed-products")
    @PreAuthorize("hasAnyAuthority('AGREEMENT_CREATE', 'AGREEMENT_VIEW_MY', 'AGREEMENT_VIEW_ALL', 'DRAFT_VIEW_MY', 'DRAFT_VIEW_ALL') or " + AGREEMENT_EDIT_VERSION_ID)
    public ResponseEntity<Page<AgreementComputedProduct>> getComputedProducts(
            @PathVariable Long id,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String divisionName,
            @RequestParam(required = false) String manufacturerName,
            @RequestParam(required = false) String manufacturerId,
            @RequestParam(required = false) String divisionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        
        int effectiveSize = Math.min(Math.max(1, size), 100);
        Sort sortObj = Sort.unsorted();
        if (sort != null && !sort.trim().isEmpty()) {
            String prop = sort;
            if ("productName".equals(sort)) prop = "productNameSnapshot";
            if ("divisionName".equals(sort)) prop = "divisionNameSnapshot";
            if ("manufacturerName".equals(sort)) prop = "manufacturerNameSnapshot";
            if ("manufacturerId".equals(sort)) prop = "manufacturerId";
            if ("divisionId".equals(sort)) prop = "divisionId";

            Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
            sortObj = Sort.by(dir, prop).and(Sort.by("id").ascending());
        } else {
            sortObj = Sort.by("productNameSnapshot").ascending().and(Sort.by("id").ascending());
        }

        Pageable pageable = PageRequest.of(page, effectiveSize, sortObj);
        
        Page<AgreementComputedProduct> result = computedProductRepository.findAll(
                AgreementComputedProductSpecification.filterBy(id, productId, productName, divisionName, manufacturerName, manufacturerId, divisionId),
                pageable
        );
        return ResponseEntity.ok(result);
    }
}
