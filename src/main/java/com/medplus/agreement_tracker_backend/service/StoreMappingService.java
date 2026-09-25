package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.response.AgreementStoreMappingResponse;
import com.medplus.agreement_tracker_backend.dto.response.ParsedStoreMappingsDto;
import com.medplus.agreement_tracker_backend.dto.response.StoreUploadResultDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StoreMappingService {

    byte[] generateTemplate();

    Page<AgreementStoreMappingResponse> searchMappings(Long agreementVersionId, Long currentUserId, String search, Pageable pageable);

    StoreUploadResultDto uploadMappings(Long agreementVersionId, MultipartFile file, Long currentUserId);

    /**
     * Stateless store Excel parse against source version geography. No DB writes.
     */
    ParsedStoreMappingsDto parseMappingsStateless(Long sourceVersionId, MultipartFile file, Long currentUserId);

    void deleteMappings(Long agreementVersionId, List<Long> mappingIds, Long currentUserId);

    void copyMappings(Long sourceVersionId, Long targetVersionId);

    com.medplus.agreement_tracker_backend.dto.response.AgreementStoreMappingResponse addCustomStore(
            Long agreementVersionId,
            com.medplus.agreement_tracker_backend.dto.request.AgreementStoreDto request,
            Long currentUserId);

    void replaceMappingsFromStoreIds(Long targetVersionId, List<com.medplus.agreement_tracker_backend.dto.request.CommercialDataPayload.StoreMappingSubmitDto> storeMappings);
}
