package com.medplus.agreement_tracker_backend.dto.response;

import java.util.List;
import java.util.Map;

public record PriceOffExcelParseResult(
        List<PriceOffParsedRowDto> validRows,
        Map<Integer, String> rowErrors
) {}
