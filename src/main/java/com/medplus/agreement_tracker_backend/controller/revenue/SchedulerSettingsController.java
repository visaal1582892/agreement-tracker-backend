package com.medplus.agreement_tracker_backend.controller.revenue;

import com.medplus.agreement_tracker_backend.entity.RevenueRecognitionSettings;
import com.medplus.agreement_tracker_backend.service.SchedulerSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/revenue-recognition/settings")
@RequiredArgsConstructor
public class SchedulerSettingsController {

    private final SchedulerSettingsService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MASTER_MANAGE', 'COMMERCIAL_PAYOUT_CALCULATE')")
    public ResponseEntity<RevenueRecognitionSettings> getSettings() {
        return ResponseEntity.ok(service.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MASTER_MANAGE', 'COMMERCIAL_PAYOUT_CALCULATE')")
    public ResponseEntity<RevenueRecognitionSettings> updateSettings(@RequestBody RevenueRecognitionSettings settings) {
        return ResponseEntity.ok(service.updateSettings(settings));
    }

    @PostMapping("/run-manual")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MASTER_MANAGE', 'COMMERCIAL_PAYOUT_CALCULATE')")
    public ResponseEntity<?> runManual(@RequestBody com.medplus.agreement_tracker_backend.dto.request.RunManualRequest payload) {
        service.runManual(payload.getMonthKeys(), payload.getSupplierId(), payload.getAgreementId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/lock/{agreementId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'MASTER_MANAGE', 'COMMERCIAL_PAYOUT_CALCULATE')")
    public ResponseEntity<?> lockPeriods(@PathVariable Long agreementId, @RequestBody com.medplus.agreement_tracker_backend.dto.request.LockRequest payload) {
        service.lockPeriods(agreementId, payload.getCalendarYear(), payload.getCalendarMonth());
        return ResponseEntity.ok().build();
    }
}
