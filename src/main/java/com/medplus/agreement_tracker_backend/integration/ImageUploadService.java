package com.medplus.agreement_tracker_backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medplus.agreement_tracker_backend.config.ImageServerProperties;
import com.medplus.agreement_tracker_backend.exception.ImageUploadException;
import com.medplus.agreement_tracker_backend.integration.dto.ImageTransitDetails;
import com.medplus.agreement_tracker_backend.integration.dto.ImageUploadServerResponse;
import com.medplus.agreement_tracker_backend.integration.dto.OAuthTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {

    private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp"),
            Map.entry("eml", "message/rfc822"),
            Map.entry("zip", "application/zip"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("csv", "text/csv"),
            Map.entry("txt", "text/plain")
    );

    private final RestClient imageServerRestClient;
    private final ImageServerProperties properties;
    private final ObjectMapper objectMapper;

    public ImageUploadServerResponse uploadFile(MultipartFile file) {
        return uploadFile(file, createUploadSession());
    }

    public UploadSession createUploadSession() {
        String oauthToken = fetchOAuthToken();
        ImageTransitDetails transit = fetchTransitDetails(oauthToken);
        return new UploadSession(transit);
    }

    public ImageUploadServerResponse uploadFile(MultipartFile file, UploadSession session) {
        return uploadBinary(file, session.transit());
    }

    public record UploadSession(ImageTransitDetails transit) {
    }

    public byte[] downloadFromUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new ImageUploadException("Unable to download file. Please try again later.");
        }
        try {
            return imageServerRestClient.get()
                    .uri(URI.create(fileUrl.trim()))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException ex) {
            log.error("Failed to download file from image server url={}", fileUrl, ex);
            throw new ImageUploadException(ImageServerErrorParser.downloadFailed());
        }
    }

    private String fetchOAuthToken() {
        String credentials = properties.getOauthClientId() + ":" + properties.getOauthClientSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        try {
            OAuthTokenResponse response = imageServerRestClient.post()
                    .uri(properties.getOauthTokenUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve()
                    .body(OAuthTokenResponse.class);

            if (response == null || !StringUtils.hasText(response.getAccessToken())) {
                log.error("Image server OAuth response missing access token");
                throw new ImageUploadException(ImageServerErrorParser.serviceUnavailable());
            }
            return response.getAccessToken();
        } catch (ImageUploadException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.error("Image server OAuth token request failed status={} body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new ImageUploadException(ImageServerErrorParser.serviceUnavailable());
        } catch (RestClientException ex) {
            log.error("Image server OAuth token request failed", ex);
            throw new ImageUploadException(ImageServerErrorParser.serviceUnavailable());
        }
    }

    private ImageTransitDetails fetchTransitDetails(String oauthToken) {
        try {
            String rawBody = imageServerRestClient.get()
                    .uri(properties.getTransitUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + oauthToken)
                    .retrieve()
                    .body(String.class);

            ImageTransitDetails transit = parseTransitDetails(rawBody);
            if (!StringUtils.hasText(transit.getImageServerUrl())
                    || !StringUtils.hasText(transit.getAccessToken())
                    || !StringUtils.hasText(transit.getClientId())) {
                log.error("Image server transit response missing required fields: {}", rawBody);
                throw new ImageUploadException(ImageServerErrorParser.serviceUnavailable());
            }
            return transit;
        } catch (ImageUploadException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.error("Image server transit request failed status={} body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new ImageUploadException(ImageServerErrorParser.serviceUnavailable());
        } catch (RestClientException ex) {
            log.error("Image server transit details request failed", ex);
            throw new ImageUploadException(ImageServerErrorParser.serviceUnavailable());
        }
    }

    private ImageTransitDetails parseTransitDetails(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            log.error("Image server transit response was empty");
            throw new ImageUploadException(ImageServerErrorParser.serviceUnavailable());
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody.trim());
            JsonNode payload = unwrapResponsePayload(root);
            return objectMapper.treeToValue(payload, ImageTransitDetails.class);
        } catch (IOException ex) {
            log.error("Failed to parse image server transit response: {}", rawBody, ex);
            throw new ImageUploadException(ImageServerErrorParser.serviceUnavailable());
        }
    }

    private ImageUploadServerResponse uploadBinary(MultipartFile file, ImageTransitDetails transit) {
        String contentType = resolveContentType(file);
        String uploadUrl = buildUploadUrl(transit);

        MultiValueMap<String, Object> multipartBody;
        try {
            multipartBody = buildMultipartBody(file, contentType);
        } catch (IOException ex) {
            log.error("Failed to read uploaded file bytes file={}", file.getOriginalFilename(), ex);
            throw new ImageUploadException(ImageServerErrorParser.genericFailure());
        }

        try {
            String rawBody = imageServerRestClient.post()
                    .uri(URI.create(uploadUrl))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody)
                    .retrieve()
                    .body(String.class);

            ImageUploadServerResponse response = parseUploadResponse(rawBody);
            if (!StringUtils.hasText(response.getImagePath())) {
                log.error("Image server upload response missing image path body={}", rawBody);
                throw new ImageUploadException(ImageServerErrorParser.fromBody(rawBody, 200));
            }
            response.setImagePath(resolvePublicUrl(transit.getImageServerUrl(), response.getImagePath()));
            response.setThumbnailPath(resolvePublicUrl(transit.getImageServerUrl(), response.getThumbnailPath()));
            return response;
        } catch (ImageUploadException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new ImageUploadException(ImageServerErrorParser.fromResponseException(ex));
        } catch (RestClientException ex) {
            log.error("Image server upload failed file={}", file.getOriginalFilename(), ex);
            throw new ImageUploadException(ImageServerErrorParser.genericFailure());
        }
    }

    private ImageUploadServerResponse parseUploadResponse(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            log.error("Image server upload response was empty");
            throw new ImageUploadException(ImageServerErrorParser.genericFailure());
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody.trim());
            JsonNode payload = unwrapResponsePayload(root);
            if (payload.isArray()) {
                if (payload.isEmpty()) {
                    throw new ImageUploadException(ImageServerErrorParser.fromBody(rawBody, 200));
                }
                payload = payload.get(0);
            }
            ImageUploadServerResponse response = objectMapper.treeToValue(payload, ImageUploadServerResponse.class);
            if (response == null) {
                throw new ImageUploadException(ImageServerErrorParser.fromBody(rawBody, 200));
            }
            return response;
        } catch (ImageUploadException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Failed to parse image server upload response: {}", rawBody, ex);
            throw new ImageUploadException(ImageServerErrorParser.fromBody(rawBody, 200));
        }
    }

    private JsonNode unwrapResponsePayload(JsonNode root) throws IOException {
        if (root.has("response")) {
            JsonNode responseNode = root.get("response");
            if (responseNode.isTextual()) {
                return objectMapper.readTree(responseNode.asText());
            }
            if (responseNode.isObject() || responseNode.isArray()) {
                return responseNode;
            }
        }
        if (root.has("data") && (root.get("data").isObject() || root.get("data").isArray())) {
            return root.get("data");
        }
        return root;
    }

    private MultiValueMap<String, Object> buildMultipartBody(MultipartFile file, String contentType) throws IOException {
        byte[] fileBytes = file.getBytes();
        String filename = file.getOriginalFilename();

        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        partHeaders.setContentDispositionFormData("files", filename);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("files", new HttpEntity<>(fileResource, partHeaders));
        return body;
    }

    private String resolvePublicUrl(String imageServerUrl, String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        String trimmedPath = path.trim();
        if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
            return trimmedPath;
        }
        if (!StringUtils.hasText(imageServerUrl)) {
            return trimmedPath;
        }
        String baseUrl = imageServerUrl.replaceAll("/+$", "");
        String normalizedPath = trimmedPath.startsWith("/") ? trimmedPath : "/" + trimmedPath;
        return baseUrl + normalizedPath;
    }

    private String buildUploadUrl(ImageTransitDetails transit) {
        String baseUrl = transit.getImageServerUrl().replaceAll("/+$", "");
        return baseUrl + "/upload"
                + "?token=" + urlEncode(transit.getAccessToken())
                + "&clientId=" + urlEncode(transit.getClientId())
                + "&imageType=" + urlEncode(properties.getImageType());
    }

    public String resolveContentType(MultipartFile file) {
        String extension = extractExtension(file.getOriginalFilename());
        if ("zip".equals(extension)) {
            return "application/zip";
        }
        if (StringUtils.hasText(file.getContentType()) && !"application/octet-stream".equals(file.getContentType())) {
            return file.getContentType();
        }
        return EXTENSION_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
