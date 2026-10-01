package com.medplus.agreement_tracker_backend.security;

import com.medplus.agreement_tracker_backend.repository.AgreementRepository;
import com.medplus.agreement_tracker_backend.repository.AgreementVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;

@Component("agreementSecurity")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgreementSecurity {

    private final AgreementRepository agreementRepository;
    private final AgreementVersionRepository agreementVersionRepository;

    public boolean isAgreementOwner(Long agreementId, Long userId) {
        if (agreementId == null || userId == null) return false;
        return agreementRepository.findById(agreementId)
                .map(a -> a.getOwner().getId().equals(userId))
                .orElse(false);
    }

    public boolean isVersionOwner(Long versionId, Long userId) {
        if (versionId == null || userId == null) return false;
        return agreementVersionRepository.findById(versionId)
                .map(v -> v.getAgreement().getOwner().getId().equals(userId))
                .orElse(false);
    }
}
