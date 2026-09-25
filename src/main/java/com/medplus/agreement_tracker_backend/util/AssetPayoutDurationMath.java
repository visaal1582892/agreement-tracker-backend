package com.medplus.agreement_tracker_backend.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Shared Asset Rentals Per-Store schedule duration math.
 * Must stay in sync with frontend {@code assetPayoutDurationUtils.js}.
 */
public final class AssetPayoutDurationMath {

    /** Average Gregorian month length — exact match with frontend constant. */
    public static final double AVERAGE_DAYS_PER_MONTH = 30.44;

    private AssetPayoutDurationMath() {
    }

    /**
     * Inclusive tenure days (expiry day counts).
     *
     * @return null when dates missing or expiry before start
     */
    public static Long inclusiveDays(LocalDate startDate, LocalDate expiryDate) {
        if (startDate == null || expiryDate == null) {
            return null;
        }
        long exclusiveDays = ChronoUnit.DAYS.between(startDate, expiryDate);
        if (exclusiveDays < 0) {
            return null;
        }
        return exclusiveDays + 1;
    }

    /**
     * {@code Math.ceil(inclusiveDays / 30.44)}.
     *
     * @return null when dates missing/invalid
     */
    public static Integer maxAllowedMonths(LocalDate startDate, LocalDate expiryDate) {
        Long days = inclusiveDays(startDate, expiryDate);
        if (days == null || days <= 0) {
            return null;
        }
        return (int) Math.ceil(days / AVERAGE_DAYS_PER_MONTH);
    }
}
