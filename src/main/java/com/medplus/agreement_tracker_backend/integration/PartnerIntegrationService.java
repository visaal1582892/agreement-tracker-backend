package com.medplus.agreement_tracker_backend.integration;

import com.medplus.agreement_tracker_backend.config.PartnerMicroserviceProperties;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationVendorResponse;
import com.medplus.agreement_tracker_backend.integration.dto.LocationOptionResponse;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalApiResponseWrapper;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalPartnerRecord;
import com.medplus.agreement_tracker_backend.integration.dto.external.PartnerSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerIntegrationService {

    private static final String DEFAULT_COUNTRY_CODE = "IN";

    private final RestClient partnerMicroserviceRestClient;
    private final PartnerMicroserviceProperties properties;
    private final ExternalApiResponseParser externalApiResponseParser;

    public List<IntegrationVendorResponse> searchPartners(String searchKey, List<String> stateCodes) {
        return searchPartners(searchKey, stateCodes, 30);
    }

    public List<IntegrationVendorResponse> searchPartners(String searchKey, List<String> stateCodes, int size) {
        if (!StringUtils.hasText(searchKey)) {
            return List.of();
        }

        String trimmed = searchKey.trim();
        boolean isNumeric = trimmed.chars().allMatch(Character::isDigit);
        if (!isNumeric && trimmed.length() < 3) {
            return List.of();
        }

        List<ExternalPartnerRecord> items = fetchPartners(trimmed, stateCodes);
        List<IntegrationVendorResponse> results = mapPartners(items);
        return results.size() <= size ? results : results.subList(0, size);
    }

    public List<IntegrationVendorResponse> getPartnersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }

        // Fire all lookups in parallel instead of sequentially to avoid N * timeout delay
        Map<Long, IntegrationVendorResponse> resolved = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = distinctIds.stream()
                .map(id -> CompletableFuture.runAsync(() -> {
                    try {
                        fetchPartners(String.valueOf(id), null).stream()
                                .filter(item -> Objects.equals(item.getAccountId(), id))
                                .findFirst()
                                .map(this::toIntegrationVendor)
                                .ifPresent(vendor -> resolved.put(id, vendor));
                    } catch (Exception ex) {
                        log.warn("getPartnersByIds: lookup failed for id={}, msg={}", id, ex.getMessage());
                    }
                }))
                .collect(Collectors.toList());

        // Wait for all parallel fetches to complete (with timeout equal to read-timeout)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception ex) {
            log.warn("getPartnersByIds: one or more parallel lookups failed, returning partial results", ex);
        }

        // Preserve original insertion order of ids
        return distinctIds.stream()
                .map(resolved::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<LocationOptionResponse> searchCountries(String q) {
        if (!isValidSearchQuery(q)) {
            return List.of();
        }
        Map<String, String> nameToCode = fetchCountries();
        return filterAndLimit(nameToCode, q.trim());
    }

    public List<LocationOptionResponse> listCountries() {
        Map<String, String> nameToCode = fetchCountries();
        if (nameToCode == null || nameToCode.isEmpty()) {
            return List.of();
        }
        return nameToCode.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> LocationOptionResponse.builder()
                        .name(entry.getKey())
                        .code(entry.getValue())
                        .build())
                .toList();
    }

    public List<LocationOptionResponse> searchStates(String q, String countryCode) {
        if (!isValidSearchQuery(q)) {
            return List.of();
        }
        String resolvedCountry = StringUtils.hasText(countryCode) ? countryCode.trim() : DEFAULT_COUNTRY_CODE;
        Map<String, String> nameToCode = fetchStates(resolvedCountry);
        return filterAndLimit(nameToCode, q.trim());
    }

    public List<LocationOptionResponse> listStates(String countryCode) {
        String resolvedCountry = StringUtils.hasText(countryCode) ? countryCode.trim() : DEFAULT_COUNTRY_CODE;
        Map<String, String> nameToCode = fetchStates(resolvedCountry);
        if (nameToCode == null || nameToCode.isEmpty()) {
            return List.of();
        }
        return nameToCode.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> LocationOptionResponse.builder()
                        .name(entry.getKey())
                        .code(entry.getValue())
                        .build())
                .toList();
    }

    public List<LocationOptionResponse> searchCities(String stateCode, String q) {
        if (!StringUtils.hasText(stateCode) || !isValidSearchQuery(q)) {
            return List.of();
        }
        Map<String, String> nameToCode = fetchCities(stateCode.trim());
        return filterAndLimit(nameToCode, q.trim());
    }

    /** All cities for a state (no search needle). Used by payout filter multi-select. */
    public List<LocationOptionResponse> listCitiesForState(String stateCode) {
        if (!StringUtils.hasText(stateCode)) {
            return List.of();
        }
        Map<String, String> nameToCode = fetchCities(stateCode.trim());
        if (nameToCode == null || nameToCode.isEmpty()) {
            return List.of();
        }
        return nameToCode.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> LocationOptionResponse.builder()
                        .name(entry.getKey())
                        .code(entry.getValue())
                        .build())
                .toList();
    }

    private List<ExternalPartnerRecord> fetchPartners(String searchKey, List<String> stateCodes) {
        PartnerSearchRequest request = PartnerSearchRequest.builder()
                .searchKey(searchKey)
                .stateCodes(stateCodes)
                .limit(String.valueOf(properties.getSearchLimit()))
                .tenantId(String.valueOf(properties.getTenantId()))
                .build();

        try {
            ExternalApiResponseWrapper wrapper = partnerMicroserviceRestClient.post()
                    .uri("/partner/get-partners-by-search-key")
                    .body(request)
                    .retrieve()
                    .body(ExternalApiResponseWrapper.class);

            return externalApiResponseParser.parsePartnerSearchList(wrapper, properties.getSearchLimit());
        } catch (RestClientException ex) {
            log.warn("Partner search failed for searchKey={}, msg={}", searchKey, ex.getMessage());
            return List.of();
        }
    }

    private Map<String, String> fetchCountries() {
        try {
            ExternalApiResponseWrapper wrapper = partnerMicroserviceRestClient.get()
                    .uri("/partner/get-countries")
                    .retrieve()
                    .body(ExternalApiResponseWrapper.class);
            return externalApiResponseParser.parseNameCodeMap(wrapper);
        } catch (RestClientException ex) {
            log.warn("Partner get-countries failed, msg={}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, String> fetchStates(String countryCode) {
        try {
            ExternalApiResponseWrapper wrapper = partnerMicroserviceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/partner/get-states")
                            .queryParam("countryCode", countryCode)
                            .build())
                    .retrieve()
                    .body(ExternalApiResponseWrapper.class);
            return externalApiResponseParser.parseNameCodeMap(wrapper);
        } catch (RestClientException ex) {
            log.warn("Partner get-states failed for countryCode={}, msg={}", countryCode, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, String> fetchCities(String stateCode) {
        try {
            ExternalApiResponseWrapper wrapper = partnerMicroserviceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/partner/get-cities-for-state")
                            .queryParam("stateCode", stateCode)
                            .build())
                    .retrieve()
                    .body(ExternalApiResponseWrapper.class);
            return externalApiResponseParser.parseNameCodeMap(wrapper);
        } catch (RestClientException ex) {
            log.warn("Partner get-cities failed for stateCode={}, msg={}", stateCode, ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<LocationOptionResponse> filterAndLimit(Map<String, String> nameToCode, String query) {
        if (nameToCode == null || nameToCode.isEmpty()) {
            return List.of();
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return nameToCode.entrySet().stream()
                .filter(entry -> matches(entry.getKey(), entry.getValue(), needle))
                .sorted(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .limit(properties.getSearchLimit())
                .map(entry -> LocationOptionResponse.builder()
                        .name(entry.getKey())
                        .code(entry.getValue())
                        .build())
                .toList();
    }

    private static boolean matches(String name, String code, String needle) {
        boolean nameMatch = name != null && name.toLowerCase(Locale.ROOT).contains(needle);
        boolean codeMatch = code != null && code.toLowerCase(Locale.ROOT).contains(needle);
        return nameMatch || codeMatch;
    }

    private static boolean isValidSearchQuery(String q) {
        return StringUtils.hasText(q) && q.trim().length() >= 2;
    }

    private List<IntegrationVendorResponse> mapPartners(List<ExternalPartnerRecord> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(this::toIntegrationVendor)
                .filter(Objects::nonNull)
                .limit(properties.getSearchLimit())
                .toList();
    }

    private IntegrationVendorResponse toIntegrationVendor(ExternalPartnerRecord item) {
        if (item == null || item.getAccountId() == null) {
            return null;
        }
        IntegrationVendorResponse.Company responseCompany = null;
        if (item.getCompany() != null) {
            responseCompany = IntegrationVendorResponse.Company.builder()
                    .state(item.getCompany().getState())
                    .city(item.getCompany().getCity())
                    .build();
        }

        return IntegrationVendorResponse.builder()
                .vendorId(item.getAccountId())
                .vendorName(item.getName())
                .state(item.getState())
                .company(responseCompany)
                .build();
    }
}
