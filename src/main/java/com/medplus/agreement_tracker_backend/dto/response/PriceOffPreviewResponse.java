package com.medplus.agreement_tracker_backend.dto.response;

import java.util.List;

public record PriceOffPreviewResponse(
        int totalRows,
        int validRows,
        int errorRows,
        boolean canCommit,
        List<PriceOffPreviewRowDto> rows
) {}
