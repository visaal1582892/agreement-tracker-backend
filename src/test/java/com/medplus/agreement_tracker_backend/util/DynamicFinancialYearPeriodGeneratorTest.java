package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicFinancialYearPeriodGeneratorTest {

    @Test
    void generatesCleanBaseNames_withoutFySuffix() {
        LocalDate start = LocalDate.of(2025, 4, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        List<DynamicFinancialYearPeriodGenerator.PeriodSpec> specs = new ArrayList<>();
        specs.addAll(DynamicFinancialYearPeriodGenerator.generatePeriodSpecs(
                PayoutFrequency.MONTHLY, start, end, 4));
        specs.addAll(DynamicFinancialYearPeriodGenerator.generatePeriodSpecs(
                PayoutFrequency.QUARTERLY, start, end, 4));
        specs.addAll(DynamicFinancialYearPeriodGenerator.generatePeriodSpecs(
                PayoutFrequency.HALF_YEARLY, start, end, 4));
        specs.addAll(DynamicFinancialYearPeriodGenerator.generatePeriodSpecs(
                PayoutFrequency.YEARLY, start, end, 4));

        assertTrue(specs.stream().anyMatch(s -> "Apr 2025".equals(s.name())));
        assertTrue(specs.stream().anyMatch(s -> "AMJ".equals(s.name())));
        assertTrue(specs.stream().anyMatch(s -> "AMJJAS".equals(s.name())));
        assertTrue(specs.stream().anyMatch(s -> "Apr 2025 – Mar 2026".equals(s.name())));
        assertTrue(specs.stream().noneMatch(s -> s.name().contains("(FY")));
    }

    @Test
    void quartersUseMonthInitialsOnly() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);

        List<DynamicFinancialYearPeriodGenerator.PeriodSpec> specs =
                DynamicFinancialYearPeriodGenerator.generatePeriodSpecs(
                        PayoutFrequency.QUARTERLY, start, end, 1);

        List<String> quarters = specs.stream()
                .map(DynamicFinancialYearPeriodGenerator.PeriodSpec::name)
                .toList();

        assertEquals(List.of("JFM", "AMJ", "JAS", "OND"), quarters);
    }
}
