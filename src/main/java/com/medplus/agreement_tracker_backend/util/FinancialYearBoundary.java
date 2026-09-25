package com.medplus.agreement_tracker_backend.util;

public final class FinancialYearBoundary {

    public record Boundary(Integer calendarYear, Integer fyStartYear, Integer fyEndYear) {}

    private FinancialYearBoundary() {}

    public static Boundary resolve(int calendarMonth, int calendarYear, Integer fyStartMonth) {
        int resolvedFyStartMonth = DynamicFinancialYearPeriodGenerator.resolveStartMonth(fyStartMonth);

        if (resolvedFyStartMonth == 1) {
            return new Boundary(calendarYear, calendarYear, calendarYear);
        }
        if (calendarMonth >= resolvedFyStartMonth) {
            return new Boundary(calendarYear, calendarYear, calendarYear + 1);
        }
        return new Boundary(calendarYear, calendarYear - 1, calendarYear);
    }

    public static String formatLabel(Integer fyStartYear, Integer fyEndYear) {
        if (fyStartYear == null || fyEndYear == null) {
            return "";
        }
        int startYy = fyStartYear % 100;
        int endYy = fyEndYear % 100;
        return startYy + "-" + endYy;
    }

    public static String formatLabel(Integer fyStartYear, Integer fyEndYear, Integer fyStartMonth) {
        if (fyStartYear == null || fyEndYear == null) {
            return "";
        }
        if (DynamicFinancialYearPeriodGenerator.resolveStartMonth(fyStartMonth) == 1) {
            return String.valueOf(fyStartYear % 100);
        }
        return formatLabel(fyStartYear, fyEndYear);
    }
}
