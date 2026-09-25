package com.medplus.agreement_tracker_backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialYearBoundaryTest {

    @Test
    void aprilStartFirstHalfUsesCurrentAndNextYear() {
        FinancialYearBoundary.Boundary boundary = FinancialYearBoundary.resolve(4, 2026, 4);
        assertEquals(2026, boundary.fyStartYear());
        assertEquals(2027, boundary.fyEndYear());
        assertEquals("26-27", FinancialYearBoundary.formatLabel(boundary.fyStartYear(), boundary.fyEndYear()));
    }

    @Test
    void julyStartBeforeFyStartUsesPreviousAndCurrentYear() {
        FinancialYearBoundary.Boundary boundary = FinancialYearBoundary.resolve(4, 2026, 7);
        assertEquals(2025, boundary.fyStartYear());
        assertEquals(2026, boundary.fyEndYear());
        assertEquals("25-26", FinancialYearBoundary.formatLabel(boundary.fyStartYear(), boundary.fyEndYear()));
    }

    @Test
    void calendarYearUsesSingleYearLabel() {
        FinancialYearBoundary.Boundary boundary = FinancialYearBoundary.resolve(4, 2026, 1);
        assertEquals(2026, boundary.fyStartYear());
        assertEquals(2026, boundary.fyEndYear());
        assertEquals("26", FinancialYearBoundary.formatLabel(boundary.fyStartYear(), boundary.fyEndYear(), 1));
    }
}
