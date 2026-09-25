package com.medplus.agreement_tracker_backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AgreementDocumentDTO(
        @NotBlank String fileUrl,
        @NotBlank String originalFileName,
        String thumbnailUrl,
        String documentType
) {
}
