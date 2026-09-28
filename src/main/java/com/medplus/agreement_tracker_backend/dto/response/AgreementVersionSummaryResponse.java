package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.enums.ApprovalStatus;
import com.medplus.agreement_tracker_backend.enums.AgreementStatus;
import com.medplus.agreement_tracker_backend.enums.RevisionType;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;

@Builder
public record AgreementVersionSummaryResponse(
        Long id,
        Long agreementId,
        String agreementName,
        Integer versionNumber,
        RevisionType revisionType,
        Long ownerId,
        String ownerName,
        LocalDate startDate,
        LocalDate expiryDate,
        ApprovalStatus approvalStatus,
        AgreementStatus computedStatus,
        AgreementStatus terminalStatus,
        LocalDateTime createdAt,
        LocalDateTime lastModifiedAt
) {}
