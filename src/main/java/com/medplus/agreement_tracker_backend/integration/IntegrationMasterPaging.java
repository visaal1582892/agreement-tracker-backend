package com.medplus.agreement_tracker_backend.integration;

import com.medplus.agreement_tracker_backend.dto.request.MasterPageRequest;
import com.medplus.agreement_tracker_backend.dto.response.PagedResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class IntegrationMasterPaging {

    private IntegrationMasterPaging() {}

    public static <T> PagedResponse<T> page(
            List<T> source,
            MasterPageRequest req,
            Map<String, Function<T, String>> filterFields,
            Map<String, Function<T, String>> sortFields) {

        List<T> filtered = source.stream()
                .filter(item -> matchesFilters(item, req.getFilters(), filterFields))
                .sorted(buildComparator(req, sortFields))
                .toList();

        int page = Math.max(req.getPage(), 0);
        int size = Math.max(req.getSize(), 1);
        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());

        return new PagedResponse<>(
                filtered.subList(fromIndex, toIndex),
                filtered.size(),
                size == 0 ? 0 : (int) Math.ceil((double) filtered.size() / size),
                page,
                size);
    }

    private static <T> boolean matchesFilters(
            T item,
            Map<String, String> filters,
            Map<String, Function<T, String>> filterFields) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            if (filter.getValue() == null || filter.getValue().isBlank()) {
                continue;
            }
            Function<T, String> extractor = filterFields.get(filter.getKey());
            if (extractor == null) {
                continue;
            }
            String value = extractor.apply(item);
            if (value == null || !value.toLowerCase(Locale.ROOT)
                    .contains(filter.getValue().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static <T> Comparator<T> buildComparator(
            MasterPageRequest req,
            Map<String, Function<T, String>> sortFields) {
        Function<T, String> sortKey = sortFields.getOrDefault(
                req.getSortBy(),
                sortFields.getOrDefault("id", item -> ""));
        Comparator<T> comparator = Comparator.comparing(
                item -> sortKey.apply(item) != null ? sortKey.apply(item) : "",
                String.CASE_INSENSITIVE_ORDER);
        if ("DESC".equalsIgnoreCase(req.getSortDirection())) {
            comparator = comparator.reversed();
        }
        return comparator;
    }
}
