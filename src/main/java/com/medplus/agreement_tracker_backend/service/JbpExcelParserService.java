package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
import org.springframework.web.multipart.MultipartFile;

public interface JbpExcelParserService {

    JbpStagedWorkbookDto parseUpload(Long agreementVersionId, MultipartFile file, Long currentUserId);

    /**
     * Stateless parse against an APPROVED/REJECTED source version.
     * On validation failure returns annotated Excel (same as DRAFT jbp-upload).
     */
    JbpStagedWorkbookDto parseUploadStateless(Long sourceVersionId, MultipartFile file, Long currentUserId);

    /**
     * Stateless parse validated against a client configuration blueprint (Edit/Renew custom slots).
     * On validation failure returns annotated Excel.
     */
    JbpStagedWorkbookDto parseUploadStatelessWithBlueprint(
            Long sourceVersionId,
            MultipartFile file,
            JbpWorkbookRequest blueprint,
            Long currentUserId);
}
