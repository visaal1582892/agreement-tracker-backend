package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.request.JbpStatelessPreviewRequest;
import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;

public interface JbpExcelGeneratorService {

    byte[] generateWorkbook(
            Long agreementVersionId,
            JbpWorkbookRequest request,
            Long currentUserId,
            Integer startMonthOverride);

    /**
     * Read-only JBP template export from source version configs (no DB writes).
     */
    byte[] exportWorkbookFromSource(Long sourceVersionId, Long currentUserId);

    /**
     * Edit/Renew: generate template from client configuration blueprint + proposed dates.
     * Does not mutate agreement commercial tables. Uses client configId (numeric string) in Excel.
     */
    byte[] generateWorkbookStateless(
            Long sourceVersionId,
            JbpStatelessPreviewRequest request,
            Long currentUserId);
}
