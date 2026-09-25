package com.medplus.agreement_tracker_backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimePeriodNameParserTest {

    @Test
    void parsesMonthlyBaseNameAnchor() {
        TimePeriodNameParser.DisplayAnchor anchor =
                TimePeriodNameParser.parseDisplayAnchor("Jan 2026").orElseThrow();
        assertEquals(1, anchor.calendarMonth());
        assertEquals(2026, anchor.calendarYear());
    }

    @Test
    void parsesLegacyGroupedQuarterAnchor() {
        assertTrue(TimePeriodNameParser.parseDisplayAnchor("04-05-06 (2026)").isPresent());
        TimePeriodNameParser.DisplayAnchor anchor =
                TimePeriodNameParser.parseDisplayAnchor("04-05-06 (2026)").orElseThrow();
        assertEquals(4, anchor.calendarMonth());
        assertEquals(2026, anchor.calendarYear());
    }

    @Test
    void resolvesQuarterBoundaryForAprilFy() {
        FinancialYearBoundary.Boundary boundary =
                TimePeriodNameParser.resolveBoundary("04-05-06 (2026)", 4);
        assertEquals(2026, boundary.calendarYear());
        assertEquals(2026, boundary.fyStartYear());
        assertEquals(2027, boundary.fyEndYear());
    }
}
