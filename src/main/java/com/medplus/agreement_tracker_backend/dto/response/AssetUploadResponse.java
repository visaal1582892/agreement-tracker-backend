package com.medplus.agreement_tracker_backend.dto.response;

public record AssetUploadResponse(
        java.util.List<String> urls,
        java.util.List<String> thumbnailUrls,
        java.util.List<String> originalFilenames,
        java.util.List<String> errors
) {
}
