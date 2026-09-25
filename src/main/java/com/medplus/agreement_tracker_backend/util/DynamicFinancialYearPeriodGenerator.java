package com.medplus.agreement_tracker_backend.util;

import com.medplus.agreement_tracker_backend.enums.PayoutFrequency;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates absolute period specs shared across agreements.
 * Base names never embed FY suffixes — those are computed at display time from
 * agreement_versions.financial_year_start_month.
 */
public final class DynamicFinancialYearPeriodGenerator {

    private static final String[] MONTH_INITIALS = {
            "", "J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"
    };

    public record PeriodSpec(String name, PayoutFrequency frequency, List<YearMonth> months) {}

    private DynamicFinancialYearPeriodGenerator() {
    }

    public static int resolveStartMonth(Integer configuredStartMonth) {
        if (configuredStartMonth == null || configuredStartMonth < 1 || configuredStartMonth > 12) {
            return 4;
        }
        return configuredStartMonth;
    }

    public static List<String> generatePeriodNames(
            PayoutFrequency frequency,
            LocalDate contractStart,
            LocalDate contractEnd,
            int financialYearStartMonth) {
        return generatePeriodSpecs(frequency, contractStart, contractEnd, financialYearStartMonth).stream()
                .map(PeriodSpec::name)
                .toList();
    }

    public static List<PeriodSpec> generatePeriodSpecs(
            PayoutFrequency frequency,
            LocalDate contractStart,
            LocalDate contractEnd,
            int financialYearStartMonth) {
        if (contractStart == null || contractEnd == null || frequency == null) {
            return List.of();
        }
        return switch (frequency) {
            case ONE_TIME -> List.of(new PeriodSpec(
                    "ONE_TIME",
                    PayoutFrequency.ONE_TIME,
                    List.of(YearMonth.from(contractStart))));
            case MONTHLY -> generateMonthlySpecs(contractStart, contractEnd);
            case QUARTERLY -> generateGroupedSpecs(contractStart, contractEnd, financialYearStartMonth, 3, 4);
            case HALF_YEARLY -> generateGroupedSpecs(contractStart, contractEnd, financialYearStartMonth, 6, 2);
            case YEARLY -> generateYearlySpecs(contractStart, contractEnd, financialYearStartMonth);
            default -> List.of();
        };
    }

    public static List<String> generateMonthlyPeriods(LocalDate contractStart, LocalDate contractEnd) {
        return generateMonthlySpecs(contractStart, contractEnd).stream().map(PeriodSpec::name).toList();
    }

    public static List<String> generateQuarterlyPeriods(
            LocalDate contractStart,
            LocalDate contractEnd,
            int financialYearStartMonth) {
        return generateGroupedSpecs(contractStart, contractEnd, financialYearStartMonth, 3, 4).stream()
                .map(PeriodSpec::name)
                .toList();
    }

    public static List<String> generateHalfYearlyPeriods(
            LocalDate contractStart,
            LocalDate contractEnd,
            int financialYearStartMonth) {
        return generateGroupedSpecs(contractStart, contractEnd, financialYearStartMonth, 6, 2).stream()
                .map(PeriodSpec::name)
                .toList();
    }

    public static List<String> generateYearlyPeriods(
            LocalDate contractStart,
            LocalDate contractEnd,
            int financialYearStartMonth) {
        return generateYearlySpecs(contractStart, contractEnd, financialYearStartMonth).stream()
                .map(PeriodSpec::name)
                .toList();
    }

    private static List<PeriodSpec> generateMonthlySpecs(LocalDate contractStart, LocalDate contractEnd) {
        List<PeriodSpec> periods = new ArrayList<>();
        YearMonth current = YearMonth.from(contractStart);
        YearMonth last = YearMonth.from(contractEnd);
        while (!current.isAfter(last)) {
            periods.add(new PeriodSpec(
                    formatMonthlyName(current),
                    PayoutFrequency.MONTHLY,
                    List.of(current)));
            current = current.plusMonths(1);
        }
        return periods;
    }

    private static List<PeriodSpec> generateGroupedSpecs(
            LocalDate contractStart,
            LocalDate contractEnd,
            int financialYearStartMonth,
            int monthsPerPeriod,
            int periodsPerFy) {
        List<PeriodSpec> periods = new ArrayList<>();
        YearMonth contractStartYm = YearMonth.from(contractStart);
        YearMonth contractEndYm = YearMonth.from(contractEnd);
        YearMonth fyAnchor = alignToFinancialYearStart(contractStartYm, financialYearStartMonth);
        PayoutFrequency frequency = monthsPerPeriod == 3 ? PayoutFrequency.QUARTERLY : PayoutFrequency.HALF_YEARLY;

        while (!fyAnchor.isAfter(contractEndYm)) {
            for (int index = 0; index < periodsPerFy; index++) {
                YearMonth periodStart = fyAnchor.plusMonths((long) index * monthsPerPeriod);
                YearMonth periodEnd = periodStart.plusMonths(monthsPerPeriod - 1L);
                if (periodOverlapsContract(periodStart, periodEnd, contractStartYm, contractEndYm)) {
                    List<YearMonth> months = monthsBetweenInclusive(periodStart, periodEnd);
                    periods.add(new PeriodSpec(
                            formatInitialsName(months),
                            frequency,
                            months));
                }
            }
            fyAnchor = fyAnchor.plusMonths(12);
        }
        return periods;
    }

    private static List<PeriodSpec> generateYearlySpecs(
            LocalDate contractStart,
            LocalDate contractEnd,
            int financialYearStartMonth) {
        List<PeriodSpec> periods = new ArrayList<>();
        YearMonth contractStartYm = YearMonth.from(contractStart);
        YearMonth contractEndYm = YearMonth.from(contractEnd);
        YearMonth fyStart = alignToFinancialYearStart(contractStartYm, financialYearStartMonth);

        while (!fyStart.isAfter(contractEndYm)) {
            YearMonth fyEnd = fyStart.plusMonths(11);
            if (periodOverlapsContract(fyStart, fyEnd, contractStartYm, contractEndYm)) {
                List<YearMonth> months = monthsBetweenInclusive(fyStart, fyEnd);
                periods.add(new PeriodSpec(
                        formatAbsoluteYearRangeName(fyStart, fyEnd),
                        PayoutFrequency.YEARLY,
                        months));
            }
            fyStart = fyStart.plusMonths(12);
        }
        return periods;
    }

    public static String formatMonthlyName(YearMonth yearMonth) {
        String abbr = yearMonth.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return abbr + " " + yearMonth.getYear();
    }

    public static String formatInitialsName(List<YearMonth> months) {
        StringBuilder builder = new StringBuilder();
        for (YearMonth month : months) {
            builder.append(MONTH_INITIALS[month.getMonthValue()]);
        }
        return builder.toString();
    }

    public static String formatAbsoluteYearRangeName(YearMonth start, YearMonth end) {
        return formatMonthYear(start) + " – " + formatMonthYear(end);
    }

    public static String formatStartMonthAbbreviation(int financialYearStartMonth) {
        return Month.of(financialYearStartMonth)
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    /** @deprecated Prefer {@link #formatAbsoluteYearRangeName(YearMonth, YearMonth)}. */
    @Deprecated
    public static String formatFinancialYearName(YearMonth fyStart, int financialYearStartMonth) {
        return formatAbsoluteYearRangeName(fyStart, fyStart.plusMonths(11));
    }

    /** @deprecated Prefer {@link #formatInitialsName(List)}. */
    @Deprecated
    public static String formatGroupedMonthsName(YearMonth start, int monthCount) {
        return formatInitialsName(monthsBetweenInclusive(start, start.plusMonths(monthCount - 1L)));
    }

    /** @deprecated Prefer {@link #formatInitialsName(List)}. */
    @Deprecated
    public static String formatGroupedMonthsName(
            YearMonth start,
            int monthCount,
            int fyAnchorYear,
            int financialYearStartMonth) {
        return formatGroupedMonthsName(start, monthCount);
    }

    public static YearMonth alignToQuarterStart(YearMonth anchor, int financialYearStartMonth) {
        YearMonth fyStart = alignToFinancialYearStart(anchor, financialYearStartMonth);
        YearMonth periodStart = fyStart;
        for (int index = 0; index < 4; index++) {
            YearMonth periodEnd = periodStart.plusMonths(2);
            if (!anchor.isBefore(periodStart) && !anchor.isAfter(periodEnd)) {
                return periodStart;
            }
            periodStart = periodStart.plusMonths(3);
        }
        return fyStart;
    }

    public static YearMonth alignToHalfYearStart(YearMonth anchor, int financialYearStartMonth) {
        YearMonth fyStart = alignToFinancialYearStart(anchor, financialYearStartMonth);
        YearMonth secondHalfStart = fyStart.plusMonths(6);
        if (!anchor.isBefore(secondHalfStart)) {
            return secondHalfStart;
        }
        return fyStart;
    }

    public static YearMonth alignToFinancialYearStart(YearMonth anchor, int financialYearStartMonth) {
        YearMonth candidate = YearMonth.of(anchor.getYear(), financialYearStartMonth);
        if (anchor.isBefore(candidate)) {
            return candidate.minusYears(1);
        }
        return candidate;
    }

    static int resolveDisplayYear(int blockStartMonth, int fyAnchorYear, int financialYearStartMonth) {
        if (blockStartMonth < financialYearStartMonth || (financialYearStartMonth != 1 && blockStartMonth == 1)) {
            return fyAnchorYear + 1;
        }
        return fyAnchorYear;
    }

    private static String formatMonthYear(YearMonth yearMonth) {
        return yearMonth.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                + " " + yearMonth.getYear();
    }

    private static List<YearMonth> monthsBetweenInclusive(YearMonth start, YearMonth end) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth cursor = start;
        while (!cursor.isAfter(end)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private static boolean periodOverlapsContract(
            YearMonth periodStart,
            YearMonth periodEnd,
            YearMonth contractStart,
            YearMonth contractEnd) {
        return !periodEnd.isBefore(contractStart) && !periodStart.isAfter(contractEnd);
    }
}
