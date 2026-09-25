package com.medplus.agreement_tracker_backend.util;

import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses absolute base period names (and legacy formats) to a calendar anchor
 * used for FY boundary math against an agreement's fy_start_month.
 */
public final class TimePeriodNameParser {

    private static final Map<String, Integer> MONTH_ABBREVIATIONS = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEB", 2), Map.entry("MAR", 3),
            Map.entry("APR", 4), Map.entry("MAY", 5), Map.entry("JUN", 6),
            Map.entry("JUL", 7), Map.entry("AUG", 8), Map.entry("SEP", 9),
            Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12));

    private static final Pattern MONTHLY_NAME_PATTERN = Pattern.compile(
            "^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{4})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_MONTHLY_PATTERN = Pattern.compile("^(\\d{2})-(\\d{4})$");
    private static final Pattern LEGACY_GROUPED_MONTHS_PATTERN =
            Pattern.compile("^(\\d{2}(?:-\\d{2})+)\\s+\\((\\d{4})\\)$");
    private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile(
            "^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{4})"
                    + "\\s+[–-]\\s+"
                    + "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{4})$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FINANCIAL_YEAR_PATTERN =
            Pattern.compile("^FY\\s+(\\d{4})-(\\d{4})\\s+\\(Starts\\s+([A-Za-z]{3})\\)$");

    public record DisplayAnchor(int calendarMonth, int calendarYear) {}

    private TimePeriodNameParser() {}

    public static Optional<DisplayAnchor> parseDisplayAnchor(String name) {
        return parseDisplayAnchor(name, null);
    }

    public static Optional<DisplayAnchor> parseDisplayAnchor(String name, List<YearMonth> includedMonths) {
        if (includedMonths != null && !includedMonths.isEmpty()) {
            YearMonth earliest = includedMonths.stream().min(YearMonth::compareTo).orElse(null);
            if (earliest != null) {
                return Optional.of(new DisplayAnchor(earliest.getMonthValue(), earliest.getYear()));
            }
        }
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String trimmed = name.trim();

        Matcher monthlyName = MONTHLY_NAME_PATTERN.matcher(trimmed);
        if (monthlyName.matches()) {
            Integer month = MONTH_ABBREVIATIONS.get(monthlyName.group(1).toUpperCase(Locale.ENGLISH));
            if (month != null) {
                return Optional.of(new DisplayAnchor(month, Integer.parseInt(monthlyName.group(2))));
            }
        }

        Matcher legacyMonthly = LEGACY_MONTHLY_PATTERN.matcher(trimmed);
        if (legacyMonthly.matches()) {
            return Optional.of(new DisplayAnchor(
                    Integer.parseInt(legacyMonthly.group(1)),
                    Integer.parseInt(legacyMonthly.group(2))));
        }

        Matcher yearRange = YEAR_RANGE_PATTERN.matcher(trimmed);
        if (yearRange.matches()) {
            Integer month = MONTH_ABBREVIATIONS.get(yearRange.group(1).toUpperCase(Locale.ENGLISH));
            if (month != null) {
                return Optional.of(new DisplayAnchor(month, Integer.parseInt(yearRange.group(2))));
            }
        }

        Matcher legacyGrouped = LEGACY_GROUPED_MONTHS_PATTERN.matcher(trimmed);
        if (legacyGrouped.matches()) {
            String firstToken = legacyGrouped.group(1).substring(0, 2);
            return Optional.of(new DisplayAnchor(
                    Integer.parseInt(firstToken),
                    Integer.parseInt(legacyGrouped.group(2))));
        }

        Matcher financialYear = FINANCIAL_YEAR_PATTERN.matcher(trimmed);
        if (financialYear.matches()) {
            Integer startMonth = MONTH_ABBREVIATIONS.get(financialYear.group(3).toUpperCase(Locale.ENGLISH));
            int calendarMonth = startMonth != null ? startMonth : 1;
            return Optional.of(new DisplayAnchor(
                    calendarMonth,
                    Integer.parseInt(financialYear.group(1))));
        }

        return Optional.empty();
    }

    public static FinancialYearBoundary.Boundary resolveBoundary(String name, Integer fyStartMonth) {
        return resolveBoundary(name, fyStartMonth, null);
    }

    public static FinancialYearBoundary.Boundary resolveBoundary(
            String name,
            Integer fyStartMonth,
            List<YearMonth> includedMonths) {
        return parseDisplayAnchor(name, includedMonths)
                .map(anchor -> FinancialYearBoundary.resolve(
                        anchor.calendarMonth(),
                        anchor.calendarYear(),
                        fyStartMonth))
                .orElse(new FinancialYearBoundary.Boundary(null, null, null));
    }
}
