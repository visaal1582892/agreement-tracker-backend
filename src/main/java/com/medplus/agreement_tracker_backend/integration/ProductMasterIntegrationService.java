package com.medplus.agreement_tracker_backend.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.medplus.agreement_tracker_backend.enums.ProductMasterStatusEnum;
import com.medplus.agreement_tracker_backend.exception.BusinessException;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationDivisionResponse;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationManufacturerResponse;
import com.medplus.agreement_tracker_backend.integration.dto.IntegrationProductResponse;
import com.medplus.agreement_tracker_backend.integration.dto.PaginatedResponse;
import com.medplus.agreement_tracker_backend.integration.dto.ProductPageResult;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalApiResponseWrapper;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalDivisionItem;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalGetManufacturerRequest;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalManufacturerSearchItem;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalManufacturerWithDivisions;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductItem;
import com.medplus.agreement_tracker_backend.integration.dto.external.ExternalProductSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductMasterIntegrationService {

    private static final TypeReference<List<ExternalManufacturerSearchItem>> MANUFACTURER_SEARCH_LIST_TYPE =
            new TypeReference<>() {};

    private static final int PRODUCT_LOOKUP_CHUNK_SIZE = 100;
    private static final int PRODUCT_LOOKUP_POOL_SIZE = 20;
    private static final long PRODUCT_LOOKUP_TIMEOUT_SECONDS = 60;

    private final RestClient productMicroserviceRestClient;
    private final ExternalApiResponseParser externalApiResponseParser;

    public List<IntegrationManufacturerResponse> searchManufacturers(String searchKey) {
        return searchManufacturers(searchKey, 30);
    }

    public List<IntegrationManufacturerResponse> searchManufacturers(String searchKey, int size) {
        if (!StringUtils.hasText(searchKey)) {
            throw new BusinessException("Manufacturer search key is required");
        }

        List<String> bulkIds = extractBulkNumericIds(searchKey);
        if (bulkIds != null && !bulkIds.isEmpty()) {
            List<Long> ids = bulkIds.stream().map(Long::valueOf).distinct().toList();
            return new ArrayList<>(getManufacturersByIds(ids).values());
        }

        try {
            ExternalApiResponseWrapper wrapper = productMicroserviceRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/product/search-manufacturers")
                            .queryParam("searchKey", searchKey.trim())
                            .queryParam("status", ProductMasterStatusEnum.ACTIVE.name())
                            .build())
                    .retrieve()
                    .body(ExternalApiResponseWrapper.class);

            List<ExternalManufacturerSearchItem> items = externalApiResponseParser.parsePayloadList(
                    wrapper, MANUFACTURER_SEARCH_LIST_TYPE);

            if (items.isEmpty()) {
                return List.of();
            }

            List<IntegrationManufacturerResponse> results = items.stream()
                    .map(item -> IntegrationManufacturerResponse.builder()
                            .id(item.getManufacturerId())
                            .manufacturerName(item.getManufacturerName())
                            .status(item.getStatus())
                            .build())
                    .toList();
            return results.size() <= size ? results : results.subList(0, size);
        } catch (RestClientException ex) {
            log.error("Manufacturer search failed for searchKey={}", searchKey, ex);
            throw new BusinessException("Failed to fetch manufacturers from microservice.");
        }
    }

    public List<IntegrationDivisionResponse> getDivisionsByManufacturerIds(List<Long> manufacturerIds) {
        if (manufacturerIds == null || manufacturerIds.isEmpty()) {
            return List.of();
        }

        ExternalGetManufacturerRequest request = ExternalGetManufacturerRequest.builder()
                .manufacturerIds(manufacturerIds)
                .manufacturerStatus(ProductMasterStatusEnum.ACTIVE)
                .isDivisionRequired(true)
                .build();

        try {
            ExternalApiResponseWrapper wrapper = productMicroserviceRestClient.post()
                    .uri("/product/get-manufacturer")
                    .body(request)
                    .retrieve()
                    .body(ExternalApiResponseWrapper.class);

            List<ExternalManufacturerWithDivisions> items = externalApiResponseParser.parseManufacturerDivisionsList(wrapper);

            if (items.isEmpty()) {
                return List.of();
            }

            List<IntegrationDivisionResponse> divisions = new ArrayList<>();
            for (ExternalManufacturerWithDivisions manufacturer : items) {
                for (ExternalDivisionItem division : manufacturer.resolvedDivisions()) {
                    divisions.add(IntegrationDivisionResponse.builder()
                            .id(division.resolvedDivisionId())
                            .divisionName(division.resolvedDivisionName())
                            .manufacturerId(manufacturer.getManufacturerId())
                            .manufacturerName(manufacturer.resolvedManufacturerName())
                            .status(division.getStatus())
                            .build());
                }
            }
            return divisions;
        } catch (RestClientException ex) {
            log.error("Division fetch failed for manufacturerIds={}", manufacturerIds, ex);
            throw new BusinessException("Failed to fetch divisions from Product Microservice: " + ex.getMessage());
        }
    }

    public PaginatedResponse<IntegrationDivisionResponse> getDivisionsPaginated(
            List<Long> manufacturerIds,
            String searchKey,
            int page,
            int size,
            List<String> pinnedDivisionIds) {
        List<IntegrationDivisionResponse> allDivisions = getDivisionsByManufacturerIds(manufacturerIds);

        List<IntegrationDivisionResponse> filtered = allDivisions;
        if (StringUtils.hasText(searchKey)) {
            List<String> bulkIds = extractBulkNumericIds(searchKey);
            if (bulkIds != null && !bulkIds.isEmpty()) {
                List<Long> ids = bulkIds.stream().map(Long::valueOf).toList();
                filtered = allDivisions.stream()
                        .filter(division -> ids.contains(division.getId()))
                        .toList();
            } else {
                String lowerKey = searchKey.toLowerCase().trim();
                filtered = allDivisions.stream()
                        .filter(division ->
                                (division.getDivisionName() != null
                                        && division.getDivisionName().toLowerCase().contains(lowerKey))
                                        || String.valueOf(division.getId()).contains(lowerKey.trim()))
                        .toList();
            }
        }

        List<IntegrationDivisionResponse> ordered = filtered.stream()
                .sorted(Comparator
                        .comparing(IntegrationDivisionResponse::getDivisionName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(IntegrationDivisionResponse::getId))
                .toList();

        if (pinnedDivisionIds != null && !pinnedDivisionIds.isEmpty()) {
            java.util.Set<Long> pinnedIds = pinnedDivisionIds.stream().map(Long::valueOf).collect(java.util.stream.Collectors.toSet());
            List<IntegrationDivisionResponse> pinned = ordered.stream()
                    .filter(division -> pinnedIds.contains(division.getId()))
                    .toList();
            List<IntegrationDivisionResponse> rest = ordered.stream()
                    .filter(division -> !pinnedIds.contains(division.getId()))
                    .toList();
            ordered = new ArrayList<>(ordered.size());
            ordered.addAll(pinned);
            ordered.addAll(rest);
        }

        List<IntegrationDivisionResponse> paged = ordered.stream()
                .skip((long) page * size)
                .limit(size)
                .toList();

        return new PaginatedResponse<>(paged, ordered.size());
    }

    public List<IntegrationProductResponse> searchProducts(String searchKey, List<Long> manufacturerIds, List<Long> divisionIds, List<Long> excludeDivisionIds, List<String> excludeProductIds) {
        List<Long> safeManufacturerIds = safeIds(manufacturerIds);
        if (safeManufacturerIds.isEmpty()) {
            log.warn("Blocked unconstrained Solr product query: Manufacturer list is empty.");
            return Collections.emptyList();
        }

        List<String> bulkIds = extractBulkAlphanumericIds(searchKey);
        List<Long> safeDivisionIds = safeIds(divisionIds);
        List<Long> safeExcludeDivisionIds = safeIds(excludeDivisionIds);
        List<String> safeExcludeProductIds = excludeProductIds != null ? excludeProductIds : List.of();

        boolean hasExcludeProducts = !safeExcludeProductIds.isEmpty();
        Set<String> productIds = null;
        if (bulkIds != null && !bulkIds.isEmpty()) {
            productIds = new HashSet<>(bulkIds);
        } else if (hasExcludeProducts) {
            productIds = safeExcludeProductIds.stream().map(String::valueOf).collect(Collectors.toSet());
        }

        ExternalProductSearchRequest request = ExternalProductSearchRequest.builder()
                .manufacturerIdList(safeManufacturerIds)
                .manufacturerDivisionId(safeDivisionIds)
                .manufacturerDivisionNotIn(safeDivisionIds.isEmpty() && !safeExcludeDivisionIds.isEmpty())
                .searchKey((bulkIds != null && !bulkIds.isEmpty()) ? "" : (searchKey != null ? searchKey : ""))
                .productIdList(productIds)
                .productNotIn(hasExcludeProducts)
                .isRunOnDb(false)
                .isDetailsRequired(true)
                .productStatus(ProductMasterStatusEnum.ACTIVE)
                .build();

        // When both include and exclude divisions are present, Solr receives the
        // include list above. Exclude divisions are handled by the caller's in-memory
        // safety net (applyDivisionRules) — the Solr API only supports one direction
        // per query via manufacturerDivisionId + manufacturerDivisionNotIn flag.
        if (!safeDivisionIds.isEmpty() && !safeExcludeDivisionIds.isEmpty()) {
            // Include list sent to Solr; exclude handled in-memory
        } else if (safeDivisionIds.isEmpty() && !safeExcludeDivisionIds.isEmpty()) {
            // Only exclude: use Solr's notIn flag
            request.setManufacturerDivisionId(safeExcludeDivisionIds);
        }

        try {
            List<ExternalProductItem> items = fetchProductItems(request);
            if (items.isEmpty()) {
                return List.of();
            }

            return items.stream()
                    .map(this::toIntegrationProduct)
                    .toList();
        } catch (RestClientException ex) {
            log.error("Product search failed searchKey={} manufacturerIds={} divisionIds={}",
                    searchKey, manufacturerIds, divisionIds, ex);
            throw new BusinessException("Failed to search products from Product Microservice: " + ex.getMessage());
        }
    }

    /**
     * Fetch products from Solr in paginated chunks (500 per chunk).
     * Uses the exclusion flags (manufacturerDivisionNotIn, productNotIn)
     * set on the request to let Solr handle filtering natively.
     */
    public List<IntegrationProductResponse> searchProductsFromSolr(ExternalProductSearchRequest request) {
        List<IntegrationProductResponse> all = new ArrayList<>();
        int chunkSize = 500;
        int offset = 0;

        while (true) {
            request.setMinLimit(offset);
            request.setNoOfRow(chunkSize);
            ProductPageResult page = fetchProductPage(request, offset, chunkSize);
            all.addAll(page.items().stream().map(this::toIntegrationProduct).toList());
            if (page.items().size() < chunkSize) {
                break; // Last page
            }
            offset += chunkSize;
        }
        return all;
    }

    /**
     * Ask Solr for the count only (noOfRow=0) — returns numFound without fetching documents.
     * Used by countScopedProducts for a lightweight live preview.
     */
    public long countProducts(ExternalProductSearchRequest request) {
        request.setMinLimit(0);
        request.setNoOfRow(0);
        ProductPageResult page = fetchProductPage(request, 0, 0);
        return page.totalElements();
    }

    public PaginatedResponse<IntegrationProductResponse> searchProductsPaginated(
            String searchKey,
            List<Long> manufacturerIds,
            List<Long> divisionIds,
            List<Long> excludeDivisionIds,
            int page,
            int size,
            List<String> pinnedProductIds,
            List<String> excludeProductIds) {
        List<Long> safeManufacturerIds = safeIds(manufacturerIds);
        if (safeManufacturerIds.isEmpty()) {
            return new PaginatedResponse<>(List.of(), 0L);
        }

        List<String> bulkIds = extractBulkAlphanumericIds(searchKey);

        List<Long> safeDivisionIds = safeIds(divisionIds);
        List<Long> safeExcludeDivisionIds = safeIds(excludeDivisionIds);
        List<String> safeExcludeProductIds = excludeProductIds != null ? excludeProductIds : List.of();

        boolean hasExcludeProducts = !safeExcludeProductIds.isEmpty();
        Set<String> productIds = null;
        if (bulkIds != null && !bulkIds.isEmpty()) {
            productIds = new HashSet<>(bulkIds);
        } else if (hasExcludeProducts) {
            productIds = safeExcludeProductIds.stream().map(String::valueOf).collect(Collectors.toSet());
        }

        ExternalProductSearchRequest request = ExternalProductSearchRequest.builder()
                .manufacturerIdList(safeManufacturerIds)
                .manufacturerDivisionId(safeDivisionIds)
                .manufacturerDivisionNotIn(safeDivisionIds.isEmpty() && !safeExcludeDivisionIds.isEmpty())
                .searchKey((bulkIds != null && !bulkIds.isEmpty()) ? "" : (searchKey != null ? searchKey : ""))
                .productIdList(productIds)
                .productNotIn(hasExcludeProducts)
                .isRunOnDb(false)
                .isDetailsRequired(true)
                .productStatus(ProductMasterStatusEnum.ACTIVE)
                .minLimit(page * size)
                .noOfRow(size)
                .build();

        // When both include and exclude divisions are present, Solr receives the
        // include list above; exclude is handled in-memory by the caller.
        // When only exclude: override with exclude list + notIn flag.
        if (safeDivisionIds.isEmpty() && !safeExcludeDivisionIds.isEmpty()) {
            request.setManufacturerDivisionId(safeExcludeDivisionIds);
        }

        try {
            ProductPageResult solrPage = fetchProductPage(request, page, size);
            List<IntegrationProductResponse> items = solrPage.items().stream()
                    .map(this::toIntegrationProduct)
                    .toList();

            // Apply pin ordering client-side (pinned set is usually small)
            List<IntegrationProductResponse> ordered = orderProductsForPaging(items, pinnedProductIds);
            return new PaginatedResponse<>(ordered, solrPage.totalElements());
        } catch (RestClientException ex) {
            log.error("Paginated product search failed searchKey={} manufacturerIds={} page={}/size={}",
                    searchKey, manufacturerIds, page, size, ex);
            throw new BusinessException("Failed to search products from Product Microservice: " + ex.getMessage());
        }
    }

    public Optional<IntegrationProductResponse> findProductByCode(String productCode) {
        if (!StringUtils.hasText(productCode)) {
            return Optional.empty();
        }

        ExternalProductSearchRequest request = ExternalProductSearchRequest.builder()
                .searchKey(productCode.trim())
                .isRunOnDb(false)
                .isDetailsRequired(true)
                .productStatus(ProductMasterStatusEnum.ACTIVE)
                .build();

        try {
            List<ExternalProductItem> items = fetchProductItems(request);
            if (items.isEmpty()) {
                return Optional.empty();
            }

            return items.stream()
                    .filter(product -> productCode.equalsIgnoreCase(product.getProductId()))
                    .findFirst()
                    .map(this::toIntegrationProduct)
                    .or(() -> items.stream().findFirst().map(this::toIntegrationProduct));
        } catch (RestClientException ex) {
            log.error("Product code lookup failed for productCode={}", productCode, ex);
            throw new BusinessException("Failed to look up product from Product Microservice: " + ex.getMessage());
        }
    }

    public Map<Long, IntegrationManufacturerResponse> getManufacturersByIds(List<Long> manufacturerIds) {
        if (manufacturerIds == null || manufacturerIds.isEmpty()) {
            return Map.of();
        }

        ExternalGetManufacturerRequest request = ExternalGetManufacturerRequest.builder()
                .manufacturerIds(manufacturerIds)
                .manufacturerStatus(ProductMasterStatusEnum.ACTIVE)
                .isDivisionRequired(false)
                .build();

        try {
            ExternalApiResponseWrapper wrapper = productMicroserviceRestClient.post()
                    .uri("/product/get-manufacturer")
                    .body(request)
                    .retrieve()
                    .body(ExternalApiResponseWrapper.class);

            List<ExternalManufacturerWithDivisions> items = externalApiResponseParser.parseManufacturerDivisionsList(wrapper);
            if (items.isEmpty()) {
                return Map.of();
            }

            Map<Long, IntegrationManufacturerResponse> result = new LinkedHashMap<>();
            for (ExternalManufacturerWithDivisions item : items) {
                result.put(item.getManufacturerId(), IntegrationManufacturerResponse.builder()
                        .id(item.getManufacturerId())
                        .manufacturerName(item.resolvedManufacturerName())
                        .status(item.getStatus() != null ? item.getStatus() : ProductMasterStatusEnum.ACTIVE.name())
                        .build());
            }
            return result;
        } catch (RestClientException ex) {
            log.error("Manufacturer hydration failed for ids={}", manufacturerIds, ex);
            throw new BusinessException("Failed to hydrate manufacturers from Product Microservice: " + ex.getMessage());
        }
    }

    public Map<Long, String> getDivisionNamesByIds(List<Long> manufacturerIds, List<Long> divisionIds) {
        if (divisionIds == null || divisionIds.isEmpty()) {
            return Map.of();
        }

        List<IntegrationDivisionResponse> divisions = getDivisionsByManufacturerIds(
                manufacturerIds != null && !manufacturerIds.isEmpty() ? manufacturerIds : List.of());

        return divisions.stream()
                .filter(division -> divisionIds.contains(division.getId()))
                .collect(Collectors.toMap(
                        IntegrationDivisionResponse::getId,
                        IntegrationDivisionResponse::getDivisionName,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    public Map<String, IntegrationProductResponse> getProductsByCodes(List<String> productCodes) {
        if (productCodes == null || productCodes.isEmpty()) {
            return Map.of();
        }

        List<String> uniqueCodes = productCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(code -> code.toUpperCase())
                .distinct()
                .toList();
        if (uniqueCodes.isEmpty()) {
            return Map.of();
        }

        List<List<String>> chunks = partition(uniqueCodes, PRODUCT_LOOKUP_CHUNK_SIZE);
        ExecutorService executor = Executors.newFixedThreadPool(PRODUCT_LOOKUP_POOL_SIZE);
        try {
            List<CompletableFuture<Map<String, IntegrationProductResponse>>> futures = chunks.stream()
                    .map(chunk -> CompletableFuture.supplyAsync(() -> fetchProductsChunk(chunk), executor))
                    .toList();

            CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
            all.get(PRODUCT_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<String, IntegrationProductResponse> result = new LinkedHashMap<>();
            for (CompletableFuture<Map<String, IntegrationProductResponse>> future : futures) {
                result.putAll(future.join());
            }
            return result;
        } catch (TimeoutException ex) {
            log.error("Bulk product lookup timed out after {}s for {} codes",
                    PRODUCT_LOOKUP_TIMEOUT_SECONDS, uniqueCodes.size(), ex);
            throw new BusinessException("Product validation timed out. Please retry with fewer product IDs.");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Bulk product lookup failed for {} codes", uniqueCodes.size(), ex);
            throw new BusinessException("Failed to validate products from Product Microservice: " + ex.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<String, IntegrationProductResponse> fetchProductsChunk(List<String> chunk) {
        Map<String, IntegrationProductResponse> result = new LinkedHashMap<>();
        for (String productCode : chunk) {
            findProductByCode(productCode).ifPresent(product -> {
                String key = product.getId() != null ? product.getId().toUpperCase() : productCode;
                result.put(key, product);
                result.putIfAbsent(productCode, product);
            });
        }
        return result;
    }

    private static List<List<String>> partition(List<String> source, int size) {
        List<List<String>> parts = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            parts.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return parts;
    }

    private List<IntegrationProductResponse> orderProductsForPaging(
            List<IntegrationProductResponse> products,
            List<String> pinnedProductIds) {
        List<IntegrationProductResponse> sorted = products.stream()
                .sorted(Comparator
                        .comparing(IntegrationProductResponse::getProductName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(IntegrationProductResponse::getId, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (pinnedProductIds == null || pinnedProductIds.isEmpty()) {
            return sorted;
        }

        Set<String> pinnedIds = new HashSet<>(pinnedProductIds);
        List<IntegrationProductResponse> pinned = sorted.stream()
                .filter(product -> pinnedIds.contains(product.getId()))
                .toList();
        List<IntegrationProductResponse> rest = sorted.stream()
                .filter(product -> !pinnedIds.contains(product.getId()))
                .toList();

        List<IntegrationProductResponse> ordered = new ArrayList<>(sorted.size());
        ordered.addAll(pinned);
        ordered.addAll(rest);
        return ordered;
    }

    private List<ExternalProductItem> fetchProductItems(ExternalProductSearchRequest request) {
        return fetchProductPage(request, 0, Integer.MAX_VALUE).items();
    }

    private ProductPageResult fetchProductPage(ExternalProductSearchRequest request, int page, int pageSize) {
        ExternalApiResponseWrapper wrapper = productMicroserviceRestClient.post()
                .uri("/product/get-products")
                .body(request)
                .retrieve()
                .body(ExternalApiResponseWrapper.class);

        return externalApiResponseParser.parseProductPage(wrapper, page, pageSize);
    }

    private IntegrationProductResponse toIntegrationProduct(ExternalProductItem item) {
        return IntegrationProductResponse.builder()
                .id(item.getProductId())
                .productName(item.resolvedProductName())
                .manufacturerId(item.getManufacturerId())
                .manufacturerName(item.resolvedManufacturerName())
                .divisionId(item.resolvedDivisionId())
                .divisionName(item.resolvedDivisionName())
                .categoryId(item.getCategoryId())
                .l3Category(item.resolvedL3Category())
                .mrp(item.getMrp())
                .cp(item.getCp())
                .status(item.getStatus())
                .build();
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids != null ? ids : Collections.emptyList();
    }

    private List<String> extractBulkNumericIds(String searchKey) {
        if (!StringUtils.hasText(searchKey)) {
            return null;
        }
        String[] tokens = searchKey.trim().split("[\\s,]+");
        if (tokens.length <= 1) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (!token.matches("\\d+")) {
                return null;
            }
            ids.add(token);
        }
        return ids.isEmpty() ? null : ids;
    }

    private List<String> extractBulkAlphanumericIds(String searchKey) {
        if (!StringUtils.hasText(searchKey)) {
            return null;
        }
        String[] tokens = searchKey.trim().split("[\\s,]+");
        if (tokens.length <= 1) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (!token.matches("[a-zA-Z0-9]+")) {
                return null;
            }
            ids.add(token);
        }
        return ids.isEmpty() ? null : ids;
    }
}
