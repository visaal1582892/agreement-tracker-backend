package com.medplus.agreement_tracker_backend.controller.integration;

import com.medplus.agreement_tracker_backend.integration.CompanyDatabaseService;
import com.medplus.agreement_tracker_backend.integration.dto.StoreDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/integration/company-db")
@RequiredArgsConstructor
public class CompanyDatabaseController {

    private final CompanyDatabaseService companyDatabaseService;

    @GetMapping("/stores/{storeId}")
    public ResponseEntity<StoreDetails> getStoreById(@PathVariable String storeId) {
        return companyDatabaseService.getStoreDetails(storeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stores")
    public ResponseEntity<List<StoreDetails>> getAllStores() {
        List<StoreDetails> stores = companyDatabaseService.getAllStores();
        return ResponseEntity.ok(stores);
    }
}
