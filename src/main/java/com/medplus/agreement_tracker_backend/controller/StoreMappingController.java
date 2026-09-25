package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.request.DeleteStoreMappingsRequest;
import com.medplus.agreement_tracker_backend.dto.response.AgreementStoreMappingResponse;
import com.medplus.agreement_tracker_backend.dto.response.StoreUploadResultDto;
import com.medplus.agreement_tracker_backend.security.UserPrincipal;
import com.medplus.agreement_tracker_backend.service.StoreMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.AGREEMENT_EDIT;
import static com.medplus.agreement_tracker_backend.security.RightExpressions.AGREEMENT_VIEW;

@RestController
@RequestMapping("/agreement-versions/{versionId}/stores")
@RequiredArgsConstructor
public class StoreMappingController {

    private final StoreMappingService storeMappingService;

    @GetMapping("/template")
    @PreAuthorize(AGREEMENT_EDIT)
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long versionId) {
        byte[] template = storeMappingService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=store-mapping-template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(template);
    }

    @GetMapping
    @PreAuthorize(AGREEMENT_VIEW)
    public ResponseEntity<Page<AgreementStoreMappingResponse>> searchMappings(
            @PathVariable Long versionId,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(storeMappingService.searchMappings(versionId, principal.getId(), search, pageable));
    }

    @PostMapping(value = "/parse-stateless", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AGREEMENT_EDIT)
    public ResponseEntity<com.medplus.agreement_tracker_backend.dto.response.ParsedStoreMappingsDto> parseMappingsStateless(
            @PathVariable Long versionId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(storeMappingService.parseMappingsStateless(versionId, file, principal.getId()));
    }
}
