package com.medplus.agreement_tracker_backend.service.impl;

import com.medplus.agreement_tracker_backend.dto.response.AssetUploadResponse;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.exception.ImageUploadException;
import com.medplus.agreement_tracker_backend.integration.ImageServerErrorParser;
import com.medplus.agreement_tracker_backend.integration.ImageUploadService;
import com.medplus.agreement_tracker_backend.integration.dto.ImageUploadServerResponse;
import com.medplus.agreement_tracker_backend.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    private final ImageUploadService imageUploadService;

    @Override
    public AssetUploadResponse uploadAssets(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("At least one file is required");
        }

        List<String> urls = new ArrayList<>();
        List<String> thumbnailUrls = new ArrayList<>();
        List<String> originalFilenames = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        ImageUploadService.UploadSession session;
        try {
            session = imageUploadService.createUploadSession();
        } catch (ImageUploadException ex) {
            log.error("Failed to initialize image upload session", ex);
            return buildAllFailed(files, ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to initialize image upload session", ex);
            return buildAllFailed(files, ImageServerErrorParser.serviceUnavailable());
        }

        for (MultipartFile file : files) {
            try {
                ImageUploadServerResponse uploaded = imageUploadService.uploadFile(file, session);
                urls.add(uploaded.getImagePath());
                thumbnailUrls.add(uploaded.getThumbnailPath());
                originalFilenames.add(resolveOriginalFilename(uploaded, file));
                errors.add(null);
            } catch (ImageUploadException ex) {
                log.warn("Image upload rejected for file={}: {}", file.getOriginalFilename(), ex.getMessage());
                addFailedFile(urls, thumbnailUrls, originalFilenames, errors, file, ex.getMessage());
            } catch (ResourceAccessException ex) {
                log.error("Upload timed out for file={}", file.getOriginalFilename(), ex);
                addFailedFile(urls, thumbnailUrls, originalFilenames, errors, file, ImageServerErrorParser.timeout());
            } catch (Exception ex) {
                log.error("Upload failed for file={}", file.getOriginalFilename(), ex);
                addFailedFile(urls, thumbnailUrls, originalFilenames, errors, file, ImageServerErrorParser.genericFailure());
            }
        }

        return new AssetUploadResponse(urls, thumbnailUrls, originalFilenames, errors);
    }

    @Override
    public byte[] proxyDownload(String encodedUrl, String originalFilename) {
        String decodedUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);
        validateDownloadUrl(decodedUrl);
        return imageUploadService.downloadFromUrl(decodedUrl);
    }

    private AssetUploadResponse buildAllFailed(List<MultipartFile> files, String message) {
        List<String> urls = new ArrayList<>();
        List<String> thumbnailUrls = new ArrayList<>();
        List<String> originalFilenames = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (MultipartFile file : files) {
            addFailedFile(urls, thumbnailUrls, originalFilenames, errors, file, message);
        }
        return new AssetUploadResponse(urls, thumbnailUrls, originalFilenames, errors);
    }

    private void addFailedFile(List<String> urls,
                               List<String> thumbnailUrls,
                               List<String> originalFilenames,
                               List<String> errors,
                               MultipartFile file,
                               String message) {
        urls.add(null);
        thumbnailUrls.add(null);
        originalFilenames.add(file.getOriginalFilename());
        errors.add(message);
    }

    private void validateDownloadUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("Download URL is required");
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                throw new BusinessException("Invalid download URL");
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!normalizedHost.contains("medplusindia.com")) {
                throw new BusinessException("Download URL host is not allowed");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid download URL");
        }
    }

    private String resolveOriginalFilename(ImageUploadServerResponse uploaded, MultipartFile file) {
        if (StringUtils.hasText(uploaded.getOriginalImageName())) {
            return uploaded.getOriginalImageName();
        }
        return file.getOriginalFilename();
    }
}
