package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.response.DashboardStatsResponse;
import com.medplus.agreement_tracker_backend.dto.response.ExpiringAgreementResponse;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;
import com.medplus.agreement_tracker_backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.DASHBOARD_VIEW;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize(DASHBOARD_VIEW)
    public ResponseEntity<DashboardStatsResponse> getStats(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getStats(principal));
    }

    @GetMapping("/expiring")
    @PreAuthorize(DASHBOARD_VIEW)
    public ResponseEntity<List<ExpiringAgreementResponse>> getExpiring(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(dashboardService.getExpiring(principal));
    }
}
