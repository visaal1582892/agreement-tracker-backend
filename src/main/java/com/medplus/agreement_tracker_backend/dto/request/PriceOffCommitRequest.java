package com.medplus.agreement_tracker_backend.dto.request;

import com.medplus.agreement_tracker_backend.dto.response.PriceOffPreviewRowDto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PriceOffCommitRequest(
        @NotEmpty
        @Size(max = 3000)
        List<PriceOffPreviewRowDto> rows
) {}
