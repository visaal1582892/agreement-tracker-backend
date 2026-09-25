package com.medplus.agreement_tracker_backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
public final class ImageServerErrorParser {

    private static final int MAX_USER_MESSAGE_LENGTH = 200;
    private static final Pattern INTERNAL_MESSAGE_PATTERN = Pattern.compile(
            "(?i)(oauth|bearer|token|transit|ssl|certificate|parse|exception|stacktrace|jdbc|sql|"
                    + "nullpointer|classnotfound|marigold\\.medplus|image server|internal server)"
    );

    private ImageServerErrorParser() {
    }

    public static String fromResponseException(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        log.error("Image server rejected request status={} body={}", ex.getStatusCode(), body);
        return fromBody(body, ex.getStatusCode().value());
    }

    public static String fromBody(String rawBody, int statusCode) {
        String extracted = extractMessage(rawBody);
        if (isSafeUserMessage(extracted)) {
            return truncate(extracted);
        }
        if (isFormatError(rawBody, statusCode)) {
            return "Format not supported";
        }
        return genericFailure();
    }

    public static String serviceUnavailable() {
        return "Upload service is temporarily unavailable. Please try again later.";
    }

    public static String timeout() {
        return "Upload timed out — file may not be supported";
    }

    public static String genericFailure() {
        return "Upload failed. Please try again or use a different file.";
    }

    public static String downloadFailed() {
        return "Unable to download file. Please try again later.";
    }

    private static String extractMessage(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        String trimmed = rawBody.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return isSafeUserMessage(trimmed) ? trimmed : null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(trimmed);
            for (String field : new String[] {"message", "errorMessage", "error", "error_description", "description"}) {
                JsonNode node = root.get(field);
                if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                    return node.asText().trim();
                }
            }
            JsonNode responseNode = root.get("response");
            if (responseNode != null && responseNode.isTextual() && StringUtils.hasText(responseNode.asText())) {
                return extractMessage(responseNode.asText());
            }
            if (responseNode != null && responseNode.isObject()) {
                return extractMessage(responseNode.toString());
            }
        } catch (Exception ex) {
            log.debug("Could not parse image server error body as JSON", ex);
        }
        return null;
    }

    private static boolean isFormatError(String rawBody, int statusCode) {
        if (statusCode == 415) {
            return true;
        }
        if (!StringUtils.hasText(rawBody)) {
            return false;
        }
        String normalized = rawBody.toLowerCase(Locale.ROOT);
        return normalized.contains("format")
                || normalized.contains("unsupported")
                || normalized.contains("content type")
                || normalized.contains("extension")
                || normalized.contains("file type");
    }

    private static boolean isSafeUserMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String trimmed = message.trim();
        if (trimmed.length() > MAX_USER_MESSAGE_LENGTH) {
            return false;
        }
        if (trimmed.contains("\n") || trimmed.contains("\r")) {
            return false;
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return false;
        }
        return !INTERNAL_MESSAGE_PATTERN.matcher(trimmed).find();
    }

    private static String truncate(String message) {
        return message.length() <= MAX_USER_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_USER_MESSAGE_LENGTH);
    }
}
