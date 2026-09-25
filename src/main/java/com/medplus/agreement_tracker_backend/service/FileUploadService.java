package com.medplus.agreement_tracker_backend.service;

import com.medplus.agreement_tracker_backend.dto.response.AssetUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileUploadService {

    AssetUploadResponse uploadAssets(List<MultipartFile> files);

    byte[] proxyDownload(String encodedUrl, String originalFilename);
}
