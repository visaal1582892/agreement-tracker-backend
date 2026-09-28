import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/service/impl/AgreementMapperService.java"
with open(file_path, "r") as f:
    content = f.read()

# Replace the broken toVersionSummaryResponse with a correct one
old_method = """    public AgreementVersionSummaryResponse toVersionSummaryResponse(AgreementVersion version) {
        return AgreementVersionSummaryResponse.builder()
                .id(version.getId())
                .agreementId(version.getAgreement().getId())
                .agreementName(version.getAgreement().getAgreementName())
                .versionNumber(version.getVersionNumber())
                .revisionType(version.getRevisionType())
                .ownerId(version.getAgreement().getOwner().getId())
                .ownerName(version.getAgreement().getOwner().getName())
                .startDate(version.getStartDate())
                .expiryDate(version.getExpiryDate())
                .approvalStatus(version.getApprovalStatus())
                .computedStatus(AgreementStatusResolver.resolveStatus(version.getAgreement()))
                .terminalStatus(version.getAgreement().getTerminalStatus())
                .createdBy(version.getCreatedBy())
                .createdAt(version.getCreatedAt())
                .lastModifiedBy(version.getLastModifiedBy())
                .lastModifiedAt(version.getLastModifiedAt())
                .build();
    }"""

new_method = """    public AgreementVersionSummaryResponse toVersionSummaryResponse(AgreementVersion version) {
        return AgreementVersionSummaryResponse.builder()
                .id(version.getId())
                .agreementId(version.getAgreement().getId())
                .agreementName(version.getAgreement().getAgreementName())
                .versionNumber(version.getVersionNumber())
                .revisionType(version.getRevisionType())
                .ownerId(version.getOwner().getId())
                .ownerName(version.getOwner().getFullName())
                .startDate(version.getStartDate())
                .expiryDate(version.getExpiryDate())
                .approvalStatus(version.getApprovalStatus())
                .computedStatus(statusResolver.resolve(version))
                .terminalStatus(statusResolver.resolveTerminalStatus(version))
                .createdAt(version.getCreatedAt())
                .lastModifiedAt(version.getUpdatedAt())
                .build();
    }"""

content = content.replace(old_method, new_method)

with open(file_path, "w") as f:
    f.write(content)
