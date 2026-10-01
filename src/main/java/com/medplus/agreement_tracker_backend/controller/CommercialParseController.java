package com.medplus.agreement_tracker_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medplus.agreement_tracker_backend.dto.request.JbpDateWindowRequest;
import com.medplus.agreement_tracker_backend.dto.request.JbpStatelessPreviewRequest;
import com.medplus.agreement_tracker_backend.dto.request.JbpWorkbookRequest;
import com.medplus.agreement_tracker_backend.dto.response.JbpStagedWorkbookDto;
import com.medplus.agreement_tracker_backend.dto.response.ParsedStoreMappingsDto;
import com.medplus.agreement_tracker_backend.dto.response.TimePeriodSummaryResponse;
import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;
import com.medplus.agreement_tracker_backend.service.JbpCommercialService;
import com.medplus.agreement_tracker_backend.service.JbpExcelGeneratorService;
import com.medplus.agreement_tracker_backend.service.JbpExcelParserService;
import com.medplus.agreement_tracker_backend.service.StoreMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.AGREEMENT_EDIT_SOURCE_VERSION_ID;

/**
 * Stateless commercial Excel parse/export for Edit/Renew (no DRAFT writes).
 */
@RestController
@RequestMapping("/agreements")
@RequiredArgsConstructor
public class CommercialParseController {

    private final JbpExcelParserService jbpExcelParserService;
    private final JbpExcelGeneratorService jbpExcelGeneratorService;
    private final JbpCommercialService jbpCommercialService;
    private final StoreMappingService storeMappingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/jbp-preview/time-periods")
    @PreAuthorize(AGREEMENT_EDIT_SOURCE_VERSION_ID)
    public ResponseEntity<List<TimePeriodSummaryResponse>> listPreviewPeriods(
            @RequestParam Long sourceVersionId,
            @RequestParam PayoutFrequency frequency,
            @RequestParam(required = false) Integer financialYearStartMonth,
            @Valid @RequestBody JbpDateWindowRequest dates,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(jbpCommercialService.listPeriodsForDateWindow(
                sourceVersionId,
                dates.startDate(),
                dates.expiryDate(),
                frequency,
                principal.getId(),
                financialYearStartMonth));
    }

    @PostMapping("/jbp-preview/template")
    @PreAuthorize(AGREEMENT_EDIT_SOURCE_VERSION_ID)
    public ResponseEntity<byte[]> generatePreviewTemplate(
            @RequestParam Long sourceVersionId,
            @Valid @RequestBody JbpStatelessPreviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        byte[] workbook = jbpExcelGeneratorService.generateWorkbookStateless(
                sourceVersionId, request, principal.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=JBP_Template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }

    @GetMapping("/jbp-template-export")
    @PreAuthorize(AGREEMENT_EDIT_SOURCE_VERSION_ID)
    public ResponseEntity<byte[]> exportJbpTemplate(
            @RequestParam Long sourceVersionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        byte[] workbook = jbpExcelGeneratorService.exportWorkbookFromSource(
                sourceVersionId, principal.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=JBP_Template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workbook);
    }

    @PostMapping(value = "/parse-jbp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AGREEMENT_EDIT_SOURCE_VERSION_ID)
    public ResponseEntity<JbpStagedWorkbookDto> parseJbp(
            @RequestParam Long sourceVersionId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "blueprint", required = false) String blueprintJson,
            @AuthenticationPrincipal UserPrincipal principal) throws Exception {
        if (blueprintJson != null && !blueprintJson.isBlank()) {
            JbpWorkbookRequest blueprint = objectMapper.readValue(blueprintJson, JbpWorkbookRequest.class);
            return ResponseEntity.ok(jbpExcelParserService.parseUploadStatelessWithBlueprint(
                    sourceVersionId, file, blueprint, principal.getId()));
        }
        return ResponseEntity.ok(
                jbpExcelParserService.parseUploadStateless(sourceVersionId, file, principal.getId()));
    }

    @GetMapping("/parse-stores/template")
    @PreAuthorize(AGREEMENT_EDIT_SOURCE_VERSION_ID)
    public ResponseEntity<byte[]> storeParseTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=store-mapping-template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(storeMappingService.generateTemplate());
    }

    @PostMapping(value = "/parse-stores", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AGREEMENT_EDIT_SOURCE_VERSION_ID)
    public ResponseEntity<ParsedStoreMappingsDto> parseStores(
            @RequestParam Long sourceVersionId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                storeMappingService.parseMappingsStateless(sourceVersionId, file, principal.getId()));
    }
}
