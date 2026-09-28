import re

file_path = "src/main/java/com/medplus/agreement_tracker_backend/service/impl/AgreementServiceImpl.java"
with open(file_path, "r") as f:
    content = f.read()

# Replace the one in getVersionsByAgreementId
old_block = """    public List<AgreementVersionSummaryResponse> getVersionsByAgreementId(Long agreementId, Long currentUserId) {
        Agreement parent = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", agreementId));
        enforceAgreementDraftVisibility(parent, currentUserId);

        return agreementVersionRepository.findByAgreementId(agreementId)
                .stream()
                .filter(v -> v.getApprovalStatus() != ApprovalStatus.DRAFT
                        || v.getAgreement().getOwner().getId().equals(currentUserId))
                .sorted((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
                .map(agreementMapperService::toVersionResponse)
                .toList();
    }"""

new_block = """    public List<AgreementVersionSummaryResponse> getVersionsByAgreementId(Long agreementId, Long currentUserId) {
        Agreement parent = agreementRepository.findById(agreementId)
                .orElseThrow(() -> new ResourceNotFoundException("Agreement", agreementId));
        enforceAgreementDraftVisibility(parent, currentUserId);

        return agreementVersionRepository.findByAgreementId(agreementId)
                .stream()
                .filter(v -> v.getApprovalStatus() != ApprovalStatus.DRAFT
                        || v.getAgreement().getOwner().getId().equals(currentUserId))
                .sorted((a, b) -> Integer.compare(a.getVersionNumber(), b.getVersionNumber()))
                .map(agreementMapperService::toVersionSummaryResponse)
                .toList();
    }"""

content = content.replace(old_block, new_block)

with open(file_path, "w") as f:
    f.write(content)
