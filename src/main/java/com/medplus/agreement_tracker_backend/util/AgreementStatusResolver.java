package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.entity.AgreementVersion;
import com.medplus.agreement_tracker_backend.enums.AgreementStatus;
import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AgreementStatusResolver {

    public AgreementStatus resolve(AgreementVersion version) {
        if (version.getTerminationDate() != null) {
            return AgreementStatus.TERMINATED;
        }
        if (version.getApprovalStatus() == ApprovalStatus.DRAFT) {
            return AgreementStatus.DRAFT;
        }
        if (version.getApprovalStatus() == ApprovalStatus.PENDING_APPROVAL) {
            return AgreementStatus.PENDING_APPROVAL;
        }
        if (version.getApprovalStatus() == ApprovalStatus.REJECTED) {
            return AgreementStatus.REJECTED;
        }
        if (version.getApprovalStatus() == ApprovalStatus.SUPERSEDED) {
            return AgreementStatus.SUPERSEDED;
        }
        if (version.getApprovalStatus() == ApprovalStatus.EDITED) {
            return AgreementStatus.EDITED;
        }
        if (version.getApprovalStatus() == ApprovalStatus.APPROVED) {
            LocalDate now = LocalDate.now();
            if (version.getStartDate() != null && version.getStartDate().isAfter(now)) {
                return AgreementStatus.UPCOMING;
            }
            if (version.getExpiryDate() != null && version.getExpiryDate().isBefore(now)) {
                Long currentVersionId = version.getAgreement().getCurrentVersionId();
                if (currentVersionId != null && !currentVersionId.equals(version.getId())) {
                    return AgreementStatus.SUPERSEDED;
                }
                return AgreementStatus.EXPIRED;
            }
            if (version.isInProgressFlag()) {
                return AgreementStatus.IN_PROGRESS;
            }
            return AgreementStatus.ACTIVE;
        }
        return AgreementStatus.DRAFT;
    }

    /**
     * Historical terminal context for APPROVED versions (independent of SUPERSEDED).
     * Past expiry → EXPIRED; otherwise ACTIVE (still within contract window when left).
     */
    public AgreementStatus resolveTerminalStatus(AgreementVersion version) {
        if (version == null) {
            return null;
        }
        if (version.getApprovalStatus() == ApprovalStatus.SUPERSEDED && version.getSupersededFromStatus() != null) {
            return AgreementStatus.valueOf(version.getSupersededFromStatus().name());
        }
        if (version.getApprovalStatus() != ApprovalStatus.APPROVED) {
            return null;
        }
        if (version.getExpiryDate() != null && version.getExpiryDate().isBefore(LocalDate.now())) {
            return AgreementStatus.EXPIRED;
        }
        return AgreementStatus.ACTIVE;
    }
}
