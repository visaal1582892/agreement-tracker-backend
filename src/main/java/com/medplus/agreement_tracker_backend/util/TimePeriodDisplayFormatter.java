package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.entity.AgreementTimePeriod;

import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Appends agreement-specific FY suffix to absolute base period names.
 * DB stores base labels (Jan 2026 / AMJ / Apr 2026 – Mar 2027); FY comes from agreement context.
 */
public final class TimePeriodDisplayFormatter {

    private static final Pattern LEGACY_GROUPED_MONTHS_PATTERN =
            Pattern.compile("^(\\d{2}(?:-\\d{2})+)\\s+\\((\\d{4})\\)$");
    private static final Pattern LEGACY_MONTHLY_PATTERN = Pattern.compile("^(\\d{2})-(\\d{4})$");
    private static final Pattern FINANCIAL_YEAR_PATTERN =
            Pattern.compile("^FY\\s+(\\d{4})-(\\d{4})\\s+\\(Starts\\s+[A-Za-z]{3}\\)$");
    private static final Pattern DISPLAY_WITH_FY_PATTERN =
            Pattern.compile("^(.+?)\\s+\\(FY\\s+[^)]+\\)$");
    private static final Pattern INITIALS_PATTERN = Pattern.compile("^[JFMAJSOND]{3,12}$");

    private static final String[] MONTH_INITIALS = {
            "", "J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"
    };
    private static final String[] MONTH_ABBREVIATIONS = {
            "", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private TimePeriodDisplayFormatter() {}

    public static String format(AgreementTimePeriod period, Integer fyStartMonth) {
        if (period == null) {
            return "";
        }
        List<YearMonth> months = TimePeriodDimensions.sortedIncludedMonths(period);
        return format(period.getName(), fyStartMonth, months);
    }

    /** Absolute base label only — no FY suffix (use for JBP sub-periods under a parent that already shows FY). */
    public static String formatBase(AgreementTimePeriod period) {
        if (period == null) {
            return "";
        }
        return toBaseDisplayName(
                stripFySuffix(period.getName() == null ? "" : period.getName()),
                TimePeriodDimensions.sortedIncludedMonths(period));
    }

    public static String formatBase(String backendString) {
        if (backendString == null || backendString.isBlank()) {
            return "";
        }
        return toBaseDisplayName(stripFySuffix(backendString.trim()), null);
    }

    public static String format(String backendString, Integer fyStartMonth) {
        return format(backendString, fyStartMonth, null);
    }

    public static String format(String backendString, Integer fyStartMonth, List<YearMonth> includedMonths) {
        if (backendString == null || backendString.isBlank()) {
            return "";
        }
        String trimmed = stripFySuffix(backendString.trim());
        if ("ONE_TIME".equals(trimmed) || FINANCIAL_YEAR_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        String baseName = toBaseDisplayName(trimmed, includedMonths);
        Optional<TimePeriodNameParser.DisplayAnchor> anchor =
                TimePeriodNameParser.parseDisplayAnchor(trimmed, includedMonths);
        if (anchor.isEmpty() && includedMonths != null && !includedMonths.isEmpty()) {
            YearMonth earliest = includedMonths.get(0);
            anchor = Optional.of(new TimePeriodNameParser.DisplayAnchor(
                    earliest.getMonthValue(), earliest.getYear()));
        }
        if (anchor.isEmpty()) {
            return baseName;
        }

        String fySuffix = formatFinancialYearSuffix(
                anchor.get().calendarMonth(),
                anchor.get().calendarYear(),
                fyStartMonth);
        if (fySuffix.isBlank()) {
            return baseName;
        }
        return baseName + " " + fySuffix;
    }

    public static boolean matches(String canonicalName, String cellValue, Integer fyStartMonth) {
        return matches(canonicalName, cellValue, fyStartMonth, null);
    }

    public static boolean matches(
            String canonicalName,
            String cellValue,
            Integer fyStartMonth,
            List<YearMonth> includedMonths) {
        if (canonicalName == null || cellValue == null) {
            return false;
        }
        String cell = cellValue.trim();
        return canonicalName.equals(cell)
                || format(canonicalName, fyStartMonth, includedMonths).equals(cell);
    }

    public static String stripFySuffix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Matcher matcher = DISPLAY_WITH_FY_PATTERN.matcher(value.trim());
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return value.trim();
    }

    private static String toBaseDisplayName(String backendString, List<YearMonth> includedMonths) {
        if (INITIALS_PATTERN.matcher(backendString).matches()) {
            return backendString.trim();
        }

        Matcher legacyGrouped = LEGACY_GROUPED_MONTHS_PATTERN.matcher(backendString);
        if (legacyGrouped.matches()) {
            return groupedMonthsToInitials(legacyGrouped.group(1));
        }

        if (includedMonths != null && !includedMonths.isEmpty()) {
            if (includedMonths.size() == 1) {
                return DynamicFinancialYearPeriodGenerator.formatMonthlyName(includedMonths.get(0));
            }
            if (includedMonths.size() == 12) {
                return DynamicFinancialYearPeriodGenerator.formatAbsoluteYearRangeName(
                        includedMonths.get(0),
                        includedMonths.get(includedMonths.size() - 1));
            }
        }

        Matcher legacyMonthly = LEGACY_MONTHLY_PATTERN.matcher(backendString);
        if (legacyMonthly.matches()) {
            int month = Integer.parseInt(legacyMonthly.group(1));
            int year = Integer.parseInt(legacyMonthly.group(2));
            if (month >= 1 && month <= 12) {
                return MONTH_ABBREVIATIONS[month] + " " + year;
            }
        }

        return backendString;
    }

    private static String groupedMonthsToInitials(String monthTokenString) {
        StringBuilder builder = new StringBuilder();
        for (String token : monthTokenString.split("-")) {
            int monthNumber = Integer.parseInt(token);
            if (monthNumber >= 1 && monthNumber <= 12) {
                builder.append(MONTH_INITIALS[monthNumber]);
            }
        }
        return builder.toString();
    }

    private static String formatFinancialYearSuffix(int calendarMonth, int calendarYear, Integer fyStartMonth) {
        FinancialYearBoundary.Boundary boundary = FinancialYearBoundary.resolve(
                calendarMonth,
                calendarYear,
                fyStartMonth);
        String label = FinancialYearBoundary.formatLabel(
                boundary.fyStartYear(),
                boundary.fyEndYear(),
                fyStartMonth);
        return label.isBlank() ? "" : "(FY " + label + ")";
    }
}
