package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.dto.request.JbpConfigurationBlockDto;
import com.medplus.agreement_tracker_backend.exception.IncompleteAgreementException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JbpConfigurationCollisionValidator {

    private JbpConfigurationCollisionValidator() {
    }

    /**
     * Validates that no two configuration blocks claim the same payment interval.
     * Payment intervals are the parent-level frequencies (e.g., YEARLY, QUARTERLY).
     * Each interval may only belong to one configuration group.
     */
    public static void validateNoPaymentIntervalOverlap(List<JbpConfigurationBlockDto> configurations) {
        if (configurations == null || configurations.size() < 2) {
            return;
        }
        Set<String> claimedIntervals = new HashSet<>();
        for (JbpConfigurationBlockDto config : configurations) {
            if (config.paymentIntervals() == null) {
                continue;
            }
            for (String interval : config.paymentIntervals()) {
                String normalised = interval.trim().toUpperCase();
                if (!claimedIntervals.add(normalised)) {
                    throw new IncompleteAgreementException(String.format(
                            "Configuration Collision: The payment interval [%s] cannot be assigned to multiple configurations.",
                            normalised));
                }
            }
        }
    }
}
