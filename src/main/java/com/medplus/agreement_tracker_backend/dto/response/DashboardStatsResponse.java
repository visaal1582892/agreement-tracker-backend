package com.medplus.agreement_tracker_backend.dto.response;

/**
 * Nullable metric fields — populated only when the caller holds the matching right.
 * Null means not authorized or not computed.
 */
public record DashboardStatsResponse(
        Long totalActive,
        Long expiringIn30Days,
        Long expiringIn60Days,
        Long expiringIn90Days,
        Long expired,
        Long pendingApprovalsCount,
        Long inProgress,
        Long totalTerminated,
        Long draftsCount,
        Long requiresMyActionCount,
        Long liveCampaignsCount,
        Long pendingPriceOffApprovalsCount,
        Long totalUsersCount,
        Long activeVendorsCount,
        Long activeProductsCount
) {}
