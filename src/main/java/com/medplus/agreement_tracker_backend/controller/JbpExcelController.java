package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.request.CommitJbpRequest;
import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
import com.medplus.agreement_tracker_backend.dto.response.JbpStructureHydrationResponse;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
import com.medplus.agreement_tracker_backend.dto.response.TimePeriodSummaryResponse;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;
import com.medplus.agreement_tracker_backend.service.JbpCommercialService;
import com.medplus.agreement_tracker_backend.service.JbpExcelGeneratorService;
import com.medplus.agreement_tracker_backend.service.JbpExcelParserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.AGREEMENT_EDIT_VERSION_ID;
import static com.medplus.agreement_tracker_backend.security.RightExpressions.AGREEMENT_VIEW;

@RestController
@RequestMapping("/agreement-versions/{agreementVersionId}")
@RequiredArgsConstructor
public class JbpExcelController {

    private final JbpExcelGeneratorService jbpExcelGeneratorService;
    private final JbpExcelParserService jbpExcelParserService;
    private final JbpCommercialService jbpCommercialService;

    @PostMapping("/jbp-template")
    @PreAuthorize(AGREEMENT_EDIT_VERSION_ID)
    public ResponseEntity<byte[]> downloadJbpTemplate(
            @PathVariable("agreementVersionId") Long versionId,
            @Valid @RequestBody JbpWorkbookRequest request,
            @RequestParam(required = false) Integer startMonth,
            @AuthenticationPrincipal UserPrincipal principal) {
        byte[] file = jbpExcelGeneratorService.generateWorkbook(
                versionId, request, principal.getId(), startMonth);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=jbp-workbook-" + versionId + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @PostMapping(value = "/jbp-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AGREEMENT_EDIT_VERSION_ID)
    public ResponseEntity<JbpStagedWorkbookDto> uploadJbpWorkbook(
            @PathVariable("agreementVersionId") Long versionId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                jbpExcelParserService.parseUpload(versionId, file, principal.getId()));
    }

    @PutMapping("/commit-jbp")
    @PreAuthorize(AGREEMENT_EDIT_VERSION_ID)
    public ResponseEntity<Void> commitJbpStructure(
            @PathVariable("agreementVersionId") Long versionId,
            @Valid @RequestBody CommitJbpRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        jbpCommercialService.commitJbpStructure(versionId, request, principal.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/jbp-structure")
    @PreAuthorize(AGREEMENT_EDIT_VERSION_ID + " or " + AGREEMENT_VIEW)
    public ResponseEntity<JbpStructureHydrationResponse> getJbpStructure(
            @PathVariable("agreementVersionId") Long versionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                jbpCommercialService.getJbpStructure(versionId, principal.getId()));
    }

    @GetMapping("/jbp-time-periods")
    @PreAuthorize(AGREEMENT_EDIT_VERSION_ID + " or " + AGREEMENT_VIEW)
    public ResponseEntity<List<TimePeriodSummaryResponse>> listJbpTimePeriods(
            @PathVariable("agreementVersionId") Long versionId,
            @RequestParam PayoutFrequency frequency,
            @RequestParam(required = false) Integer financialYearStartMonth,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(jbpCommercialService.listAvailablePeriods(
                versionId, frequency, principal.getId(), financialYearStartMonth));
    }
}
