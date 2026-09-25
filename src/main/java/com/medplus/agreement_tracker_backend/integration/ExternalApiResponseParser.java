package com.medplus.agreement_tracker_backend.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalApiResponseWrapper;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalManufacturerDivisionsPayload;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalManufacturerWithDivisions;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalPartnerRecord;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductItem;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductsPayload;
import com.medplus.agreement_tracker_backend.integration.dto.ProductPageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalApiResponseParser {

    private static final TypeReference<List<ExternalManufacturerWithDivisions>> MANUFACTURER_DIVISIONS_LIST_TYPE =
            new TypeReference<>() {};

    private static final TypeReference<List<ExternalProductItem>> PRODUCT_LIST_TYPE =
            new TypeReference<>() {};

    private static final TypeReference<java.util.Map<String, ExternalPartnerRecord>> PARTNER_SEARCH_MAP_TYPE =
            new TypeReference<>() {};

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public boolean isSuccessful(ExternalApiResponseWrapper wrapper) {
        if (wrapper == null) {
            return false;
        }
        if (Boolean.TRUE.equals(wrapper.getError())) {
            return false;
        }
        if (!StringUtils.hasText(wrapper.getResponse())) {
            return false;
        }
        return wrapper.getResponseStatus() == null
                || "SUCCESS".equalsIgnoreCase(wrapper.getResponseStatus());
    }

    public <T> T parsePayload(ExternalApiResponseWrapper wrapper, TypeReference<T> typeReference) {
        if (!isSuccessful(wrapper)) {
            log.warn("External API returned non-success wrapper: status={}, error={}",
                    wrapper != null ? wrapper.getResponseStatus() : null,
                    wrapper != null ? wrapper.getError() : null);
            return null;
        }

        try {
            return objectMapper.readValue(wrapper.getResponse(), typeReference);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse external API response payload: {}", wrapper.getResponse(), ex);
            throw new BusinessException("Failed to parse Product Microservice response payload");
        }
    }

    public <T> List<T> parsePayloadList(ExternalApiResponseWrapper wrapper, TypeReference<List<T>> typeReference) {
        List<T> parsed = parsePayload(wrapper, typeReference);
        return parsed != null ? parsed : Collections.emptyList();
    }

    public List<ExternalManufacturerWithDivisions> parseManufacturerDivisionsList(ExternalApiResponseWrapper wrapper) {
        if (!isSuccessful(wrapper)) {
            log.warn("External API returned non-success wrapper for manufacturer divisions: status={}, error={}",
                    wrapper != null ? wrapper.getResponseStatus() : null,
                    wrapper != null ? wrapper.getError() : null);
            return Collections.emptyList();
        }

        String payload = wrapper.getResponse().trim();
        try {
            if (payload.startsWith("{")) {
                ExternalManufacturerDivisionsPayload parsed = objectMapper.readValue(
                        payload, ExternalManufacturerDivisionsPayload.class);
                return parsed.getManufacturers() != null ? parsed.getManufacturers() : Collections.emptyList();
            }
            List<ExternalManufacturerWithDivisions> parsed = objectMapper.readValue(
                    payload, MANUFACTURER_DIVISIONS_LIST_TYPE);
            return parsed != null ? parsed : Collections.emptyList();
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse manufacturer divisions payload: {}", payload, ex);
            throw new BusinessException("Failed to parse Product Microservice manufacturer divisions payload");
        }
    }

    public Map<String, String> parseNameCodeMap(ExternalApiResponseWrapper wrapper) {
        if (!isSuccessful(wrapper)) {
            log.warn("External API returned non-success wrapper for location map: status={}, error={}",
                    wrapper != null ? wrapper.getResponseStatus() : null,
                    wrapper != null ? wrapper.getError() : null);
            return Collections.emptyMap();
        }

        String payload = wrapper.getResponse().trim();
        try {
            Map<String, String> parsed = objectMapper.readValue(payload, STRING_MAP_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return Collections.emptyMap();
            }
            return new LinkedHashMap<>(parsed);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse location name/code map payload: {}", payload, ex);
            throw new BusinessException("Failed to parse Partner Microservice location payload");
        }
    }

    public List<ExternalPartnerRecord> parsePartnerSearchList(ExternalApiResponseWrapper wrapper, int limit) {
        if (!isSuccessful(wrapper)) {
            log.warn("External API returned non-success wrapper for partner search: status={}, error={}",
                    wrapper != null ? wrapper.getResponseStatus() : null,
                    wrapper != null ? wrapper.getError() : null);
            return Collections.emptyList();
        }

        String payload = wrapper.getResponse().trim();
        try {
            java.util.Map<String, ExternalPartnerRecord> parsed = objectMapper.readValue(
                    payload, PARTNER_SEARCH_MAP_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return Collections.emptyList();
            }
            return parsed.values().stream()
                    .filter(record -> record != null
                            && record.getAccountId() != null
                            && record.isSupplierAccount())
                    .limit(limit)
                    .toList();
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse partner search payload: {}", payload, ex);
            throw new BusinessException("Failed to parse Partner Microservice response payload");
        }
    }

    public List<ExternalProductItem> parseProductList(ExternalApiResponseWrapper wrapper) {
        return parseProductPage(wrapper, 0, Integer.MAX_VALUE).items();
    }

    public ProductPageResult parseProductPage(ExternalApiResponseWrapper wrapper, int page, int pageSize) {
        if (!isSuccessful(wrapper)) {
            log.warn("External API returned non-success wrapper for products: status={}, error={}",
                    wrapper != null ? wrapper.getResponseStatus() : null,
                    wrapper != null ? wrapper.getError() : null);
            return new ProductPageResult(Collections.emptyList(), 0L);
        }

        String payload = wrapper.getResponse().trim();
        try {
            if (payload.startsWith("{")) {
                ExternalProductsPayload parsed = objectMapper.readValue(payload, ExternalProductsPayload.class);
                List<ExternalProductItem> items = parsed.resolvedProducts();
                long total = parsed.resolvedTotalElements(pageSize, items.size(), page);
                return new ProductPageResult(items, total);
            }
            List<ExternalProductItem> parsed = objectMapper.readValue(payload, PRODUCT_LIST_TYPE);
            List<ExternalProductItem> items = parsed != null ? parsed : Collections.emptyList();
            return new ProductPageResult(items, items.size());
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse product payload: {}", payload, ex);
            throw new BusinessException("Failed to parse Product Microservice product payload");
        }
    }
}
