package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.response.DashboardStatsResponse;
import com.medplus.agreement_tracker_backend.dto.response.ExpiringAgreementResponse;
import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.PriceOffApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.RightCode;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import com.medplus.agreement_tracker_backend.repository.ConsumerPriceOffCampaignRepository;
import com.medplus.agreement_tracker_backend.repository.UserRepository;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;
import com.medplus.agreement_tracker_backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AgreementVersionRepository agreementVersionRepository;
    private final ConsumerPriceOffCampaignRepository priceOffCampaignRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(UserPrincipal principal) {
        LocalDate today = LocalDate.now();
        Long ownerScope = resolveAgreementOwnerScope(principal);

        Long draftsCount = null;
        if (hasAnyRight(principal, RightCode.AGREEMENT_CREATE, RightCode.AGREEMENT_EDIT)) {
            draftsCount = agreementVersionRepository.countDraftsByOwner(principal.getId());
        }

        Long requiresMyActionCount = null;
        if (principal.hasRight(RightCode.AGREEMENT_EDIT.name())) {
            requiresMyActionCount = agreementVersionRepository.countRejectedByOwner(principal.getId());
        }

        Long pendingApprovalsCount = null;
        if (principal.hasRight(RightCode.AGREEMENT_APPROVE.name())) {
            pendingApprovalsCount = agreementVersionRepository.countPendingApprovals();
        }

        Long totalActive = null;
        Long expiringIn30Days = null;
        Long expiringIn60Days = null;
        Long expiringIn90Days = null;
        Long expired = null;
        Long inProgress = null;
        if (ownerScope != null || principal.hasRight(RightCode.AGREEMENT_VIEW_ALL.name())) {
            totalActive = agreementVersionRepository.countActive(today, ownerScope);
            expiringIn30Days = agreementVersionRepository.countApprovedExpiringBetween(
                    today, today.plusDays(30), ownerScope);
            expiringIn60Days = agreementVersionRepository.countApprovedExpiringBetween(
                    today.plusDays(31), today.plusDays(60), ownerScope);
            expiringIn90Days = agreementVersionRepository.countApprovedExpiringBetween(
                    today.plusDays(61), today.plusDays(90), ownerScope);
            expired = agreementVersionRepository.countExpiredNotInProgress(today, ownerScope);
            inProgress = agreementVersionRepository.countInProgressCurrentVersions(ownerScope);
        }

        Long liveCampaignsCount = null;
        if (principal.hasRight(RightCode.PRICE_OFF_VIEW.name())) {
            liveCampaignsCount = priceOffCampaignRepository.countLiveCampaigns(today);
        }

        Long pendingPriceOffApprovalsCount = null;
        if (principal.hasRight(RightCode.PRICE_OFF_APPROVE.name())) {
            pendingPriceOffApprovalsCount = priceOffCampaignRepository.countByApprovalStatus(
                    PriceOffApprovalStatus.PENDING_APPROVAL);
        }

        Long totalUsersCount = null;
        if (principal.hasRight(RightCode.ADMIN_USERS.name())) {
            totalUsersCount = userRepository.countByIsActiveTrue();
        }

        return new DashboardStatsResponse(
                totalActive,
                expiringIn30Days,
                expiringIn60Days,
                expiringIn90Days,
                expired,
                pendingApprovalsCount,
                inProgress,
                null,
                draftsCount,
                requiresMyActionCount,
                liveCampaignsCount,
                pendingPriceOffApprovalsCount,
                totalUsersCount,
                null,
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpiringAgreementResponse> getExpiring(UserPrincipal principal) {
        Long ownerScope = resolveAgreementOwnerScope(principal);
        if (ownerScope == null && !principal.hasRight(RightCode.AGREEMENT_VIEW_ALL.name())) {
            return Collections.emptyList();
        }

        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(90);
        return agreementVersionRepository.findApprovedExpiringWithinDays(today, limit, ownerScope)
                .stream()
                .map(version -> toExpiringResponse(version, today))
                .toList();
    }

    private Long resolveAgreementOwnerScope(UserPrincipal principal) {
        if (principal.hasRight(RightCode.AGREEMENT_VIEW_ALL.name())) {
            return null;
        }
        if (principal.hasRight(RightCode.AGREEMENT_VIEW.name())) {
            return principal.getId();
        }
        return null;
    }

    private boolean hasAnyRight(UserPrincipal principal, RightCode... codes) {
        for (RightCode code : codes) {
            if (principal.hasRight(code.name())) {
                return true;
            }
        }
        return false;
    }

    private ExpiringAgreementResponse toExpiringResponse(AgreementVersion version, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, version.getExpiryDate());
        String urgency = days < 30 ? "RED" : days < 60 ? "YELLOW" : "BLUE";
        var parent = version.getAgreement();
        var group = parent.getAgreementGroup();
        return new ExpiringAgreementResponse(
                version.getId(),
                parent.getId(),
                parent.getAgreementName(),
                group.getName(),
                parent.getOwner().getFullName(),
                version.getExpiryDate(),
                days,
                urgency
        );
    }
}
