package com.medplus.agreement_tracker_backend.controller.integration;

import com.medplus.agreement_tracker_backend.integration.PartnerIntegrationService;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationVendorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.MASTER_OR_AGREEMENT_READ;

@RestController
@RequestMapping("/integration")
@RequiredArgsConstructor
public class PartnerIntegrationController {

    private final PartnerIntegrationService partnerIntegrationService;

    @GetMapping("/vendors")
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)
    public ResponseEntity<List<IntegrationVendorResponse>> searchVendors(
            @RequestParam(value = "searchKey", required = false, defaultValue = "") String searchKey,
            @RequestParam(value = "stateCodes", required = false) List<String> stateCodes,
            @RequestParam(value = "size", defaultValue = "30") int size) {
        int effectiveSize = Math.min(size, 30);
        return ResponseEntity.ok(partnerIntegrationService.searchPartners(searchKey, stateCodes, effectiveSize));
    }

    @GetMapping("/vendors/by-ids")
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)
    public ResponseEntity<List<IntegrationVendorResponse>> getVendorsByIds(
            @RequestParam("ids") String ids) {
        List<Long> vendorIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::valueOf)
                .toList();
        return ResponseEntity.ok(partnerIntegrationService.getPartnersByIds(vendorIds));
    }
}
