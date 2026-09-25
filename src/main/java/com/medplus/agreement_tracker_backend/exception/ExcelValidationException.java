package com.medplus.agreement_tracker_backend.exception;

public class ExcelValidationException extends RuntimeException {

    private final byte[] errorWorkbook;
    private final String filename;
    private final String uploadSummaryJson;

    public ExcelValidationException(byte[] errorWorkbook) {
        this(errorWorkbook, "Upload_Errors.xlsx", null);
    }

    public ExcelValidationException(byte[] errorWorkbook, String filename) {
        this(errorWorkbook, filename, null);
    }

    public ExcelValidationException(byte[] errorWorkbook, String filename, String uploadSummaryJson) {
        super("Excel validation failed. See the annotated error file for details.");
        this.errorWorkbook = errorWorkbook;
        this.filename = filename;
        this.uploadSummaryJson = uploadSummaryJson;
    }

    public byte[] getErrorWorkbook() {
        return errorWorkbook;
    }

    public String getFilename() {
        return filename;
    }

    public String getUploadSummaryJson() {
        return uploadSummaryJson;
    }
}
