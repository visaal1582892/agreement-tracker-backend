package com.medplus.agreement_tracker_backend.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssetPayoutDurationMathTest {

    @Test
    void inclusiveDays_countsExpiryDay() {
        // 2024-01-01 .. 2024-01-31 inclusive = 31 days
        assertEquals(31L, AssetPayoutDurationMath.inclusiveDays(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));
    }

    @Test
    void maxAllowedMonths_ceilsAverageMonth() {
        // 31 / 30.44 → ceil = 2
        assertEquals(2, AssetPayoutDurationMath.maxAllowedMonths(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)));
    }

    @Test
    void maxAllowedMonths_exactTwoAverageMonths() {
        // 61 inclusive days → 61/30.44 ≈ 2.004 → ceil = 3
        assertEquals(3, AssetPayoutDurationMath.maxAllowedMonths(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)));
    }

    @Test
    void missingOrInvertedDates_returnNull() {
        assertNull(AssetPayoutDurationMath.maxAllowedMonths(null, LocalDate.of(2024, 1, 31)));
        assertNull(AssetPayoutDurationMath.maxAllowedMonths(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 1, 1)));
    }
}
