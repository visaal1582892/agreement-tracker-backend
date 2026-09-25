package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;
import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriodMonth;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimePeriodDisplayFormatterTest {

    @Test
    void appendsFySuffixToMonthlyBaseName() {
        // Jan sits before April FY start → FY 25-26
        assertEquals(
                "Jan 2026 (FY 25-26)",
                TimePeriodDisplayFormatter.format("Jan 2026", 4));
        assertEquals(
                "Apr 2026 (FY 26-27)",
                TimePeriodDisplayFormatter.format("Apr 2026", 4));
    }

    @Test
    void appendsFySuffixToQuarterInitialsUsingIncludedMonths() {
        AgreementTimePeriod period = period("AMJ", List.of(
                included(4, 2026),
                included(5, 2026),
                included(6, 2026)));

        assertEquals(
                "AMJ (FY 26-27)",
                TimePeriodDisplayFormatter.format(period, 4));
    }

    @Test
    void stripsExistingFySuffixThenReappendsFromMonthlyName() {
        assertEquals(
                "Jan 2026 (FY 25-26)",
                TimePeriodDisplayFormatter.format("Jan 2026 (FY 26-27)", 4));
    }

    @Test
    void formatsLegacyCanonicalNames() {
        assertEquals("Apr 2026 (FY 26-27)", TimePeriodDisplayFormatter.format("04-2026", 4));
        assertEquals("AMJ (FY 26-27)", TimePeriodDisplayFormatter.format("04-05-06 (2026)", 4));
        assertEquals("", TimePeriodDisplayFormatter.format((String) null, 4));
    }

    @Test
    void formatBaseOmitsFySuffix() {
        assertEquals("AMJ", TimePeriodDisplayFormatter.formatBase("AMJ (FY 26-27)"));
        assertEquals("Jan 2026", TimePeriodDisplayFormatter.formatBase("Jan 2026 (FY 25-26)"));

        AgreementTimePeriod period = period("AMJ", List.of(
                included(4, 2026),
                included(5, 2026),
                included(6, 2026)));
        assertEquals("AMJ", TimePeriodDisplayFormatter.formatBase(period));
    }

    private static AgreementTimePeriod period(String name, List<AgreementTimePeriodMonth> months) {
        AgreementTimePeriod p = new AgreementTimePeriod();
        p.setName(name);
        p.setIncludedMonths(months);
        return p;
    }

    private static AgreementTimePeriodMonth included(int month, int year) {
        return AgreementTimePeriodMonth.builder()
                .calendarMonth(month)
                .calendarYear(year)
                .build();
    }
}
