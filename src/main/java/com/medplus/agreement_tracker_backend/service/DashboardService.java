package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.response.DashboardStatsResponse;
import com.medplus.agreement_tracker_backend.dto.response.ExpiringAgreementResponse;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;

import java.util.List;

public interface DashboardService {

    DashboardStatsResponse getStats(UserPrincipal principal);

    List<ExpiringAgreementResponse> getExpiring(UserPrincipal principal);
}
