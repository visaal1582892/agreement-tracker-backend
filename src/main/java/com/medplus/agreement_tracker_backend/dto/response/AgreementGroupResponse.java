package com.medplus.agreement_tracker_backend.dto.response;

import java.time.LocalDateTime;

public record AgreementGroupResponse(
        Long id,
        String name,
        boolean isActive,
        Long createdByUserId,
        String createdByName,
        LocalDateTime lastModifiedAt,
        String lastModifiedByName,
        boolean canDelete
) {}
