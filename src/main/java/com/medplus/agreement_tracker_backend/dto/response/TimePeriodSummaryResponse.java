package com.medplus.agreement_tracker_backend.dto.response;

import com.medplus.agreement_tracker_backend.util.FinancialYearBoundary;

public record TimePeriodSummaryResponse(
        Long id,
        String name,
        String periodFrequency,
        Integer periodYear,
        Integer monthNumber,
        Integer calendarYear,
        Integer fyStartYear,
        Integer fyEndYear
) {
    public String financialYearLabel() {
        return FinancialYearBoundary.formatLabel(fyStartYear, fyEndYear);
    }
}
