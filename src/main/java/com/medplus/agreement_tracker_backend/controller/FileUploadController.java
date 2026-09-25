package com.medplus.agreement_tracker_backend.controller;

import com.medplus.agreement_tracker_backend.dto.response.AssetUploadResponse;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

import static com.medplus.agreement_tracker_backend.security.RightExpressions.AGREEMENT_EDIT;
import static com.medplus.agreement_tracker_backend.security.RightExpressions.AGREEMENT_VIEW;

@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping(value = "/asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(AGREEMENT_EDIT)
    public ResponseEntity<AssetUploadResponse> uploadAssets(@RequestParam("files") List<MultipartFile> files) {
        AssetUploadResponse response = fileUploadService.uploadAssets(files);
        if (isAllFailed(response)) {
            boolean formatOnly = response.errors().stream()
                    .filter(Objects::nonNull)
                    .allMatch(error -> error.toLowerCase().contains("format"));
            HttpStatus status = formatOnly ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/proxy-download")
    @PreAuthorize(AGREEMENT_VIEW)
    public ResponseEntity<byte[]> proxyDownload(
            @RequestParam("url") String encodedUrl,
            @RequestParam(value = "filename", required = false) String filename) {
        if (!StringUtils.hasText(encodedUrl)) {
            throw new BusinessException("Download URL is required");
        }

        byte[] fileBytes = fileUploadService.proxyDownload(encodedUrl, filename);
        String resolvedFilename = StringUtils.hasText(filename) ? filename : "download";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resolvedFilename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileBytes);
    }

    private boolean isAllFailed(AssetUploadResponse response) {
        if (response.urls() == null || response.urls().isEmpty()) {
            return true;
        }
        return response.urls().stream().allMatch(url -> !StringUtils.hasText(url));
    }
}
