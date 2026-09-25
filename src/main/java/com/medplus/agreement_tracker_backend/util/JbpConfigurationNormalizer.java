package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.dto.request.JbpConfigurationBlockDto;

import java.util.List;
import java.util.Map;

public final class JbpConfigurationNormalizer {

    private JbpConfigurationNormalizer() {
    }

    public static JbpConfigurationBlockDto normalize(Map<String, Object> raw) {
        String configId = stringValue(raw.get("configId"));
        
        @SuppressWarnings("unchecked")
        List<String> paymentIntervals = raw.get("paymentIntervals") instanceof List<?> list
                ? list.stream().map(JbpConfigurationNormalizer::stringValue).toList()
                : List.of();

        @SuppressWarnings("unchecked")
        List<String> targetIntervals = raw.get("targetIntervals") instanceof List<?> list
                ? list.stream().map(JbpConfigurationNormalizer::stringValue).toList()
                : List.of();

        Integer maxSlabs = raw.get("maxSlabs") instanceof Number number
                ? number.intValue()
                : 1;

        return new JbpConfigurationBlockDto(configId, paymentIntervals, targetIntervals, maxSlabs);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
