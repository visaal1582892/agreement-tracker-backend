package com.medplus.agreement_tracker_backend.exception;

import java.util.List;

/**
 * Stateless Excel parse failed with row-level validation errors (JSON response, not annotated workbook).
 */
public class ExcelParseValidationException extends RuntimeException {

    private final List<RowError> rowErrors;

    public ExcelParseValidationException(String message, List<RowError> rowErrors) {
        super(message);
        this.rowErrors = rowErrors == null ? List.of() : List.copyOf(rowErrors);
    }

    public List<RowError> getRowErrors() {
        return rowErrors;
    }

    public record RowError(String sheet, Integer row, String message) {}
}
