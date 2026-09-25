package com.medplus.agreement_tracker_backend.dto.response.master;

import java.util.List;

public record RoleRightsMatrixResponse(
        Long roleId,
        String roleName,
        List<String> rightCodes
) {}
