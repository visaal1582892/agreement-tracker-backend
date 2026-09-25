package com.medplus.agreement_tracker_backend.controller.integration;

import com.medplus.agreement_tracker_backend.integration.ProductMasterIntegrationService;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationDivisionsRequest;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationManufacturerResponse;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationDivisionResponse;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductsRequest;
import com.medplus.agreement_tracker_backend.integration.dto.PaginatedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.ArrayList;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.MASTER_OR_AGREEMENT_READ;

@RestController
@RequestMapping("/integration")
@RequiredArgsConstructor
public class ProductIntegrationController {

    private final ProductMasterIntegrationService integrationService;

    @GetMapping("/manufacturers")
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)
    public ResponseEntity<List<IntegrationManufacturerResponse>> searchManufacturers(
            @RequestParam(value = "searchKey", defaultValue = "") String searchKey,
            @RequestParam(value = "size", defaultValue = "30") int size) {
        int effectiveSize = Math.min(size, 30);
        return ResponseEntity.ok(integrationService.searchManufacturers(searchKey, effectiveSize));
    }

    @GetMapping("/manufacturers/by-ids")
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)
    public ResponseEntity<List<IntegrationManufacturerResponse>> getManufacturersByIds(
            @RequestParam("ids") String ids) {
        List<Long> manufacturerIds = java.util.Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(val -> !val.isEmpty())
                .map(Long::valueOf)
                .toList();
        return ResponseEntity.ok(new ArrayList<>(integrationService.getManufacturersByIds(manufacturerIds).values()));
    }

    @PostMapping("/divisions")
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)
    public ResponseEntity<?> getDivisions(
            @Valid @RequestBody IntegrationDivisionsRequest request) {
        if (request.getPage() != null && request.getSize() != null) {
            return ResponseEntity.ok(integrationService.getDivisionsPaginated(
                    request.getManufacturerIds(),
                    request.getSearchKey(),
                    request.getPage(),
                    request.getSize(),
                    request.getPinnedDivisionIds()));
        } else {
            return ResponseEntity.ok(integrationService.getDivisionsByManufacturerIds(request.getManufacturerIds()));
        }
    }

    @GetMapping("/divisions/by-ids")
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)
    public ResponseEntity<List<IntegrationDivisionResponse>> getDivisionsByIds(
            @RequestParam("manufacturerIds") String manufacturerIdsStr,
            @RequestParam("ids") String ids) {
        List<Long> manufacturerIds = java.util.Arrays.stream(manufacturerIdsStr.split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).map(Long::valueOf).toList();
        List<Long> divisionIds = java.util.Arrays.stream(ids.split(","))
                .map(String::trim).filter(v -> !v.isEmpty()).map(Long::valueOf).toList();
        List<IntegrationDivisionResponse> allDivisions = integrationService.getDivisionsByManufacturerIds(manufacturerIds);
        List<IntegrationDivisionResponse> filtered = allDivisions.stream()
                .filter(d -> divisionIds.contains(d.getId()))
                .toList();
        return ResponseEntity.ok(filtered);
    }

    @PostMapping("/products")
    @PreAuthorize(MASTER_OR_AGREEMENT_READ)
    public ResponseEntity<?> searchProducts(
            @Valid @RequestBody IntegrationProductsRequest request) {
        if (request.getPage() != null && request.getSize() != null) {
            return ResponseEntity.ok(integrationService.searchProductsPaginated(
                    request.getSearchKey(),
                    request.getManufacturerIds(),
                    request.getDivisionIds(),
                    request.getExcludeDivisionIds(),
                    request.getPage(),
                    request.getSize(),
                    request.getPinnedProductIds(),
                    request.getExcludeProductIds()));
        } else {
            return ResponseEntity.ok(integrationService.searchProducts(
                    request.getSearchKey(),
                    request.getManufacturerIds(),
                    request.getDivisionIds(),
                    request.getExcludeDivisionIds(),
                    request.getExcludeProductIds()));
        }
    }
}
