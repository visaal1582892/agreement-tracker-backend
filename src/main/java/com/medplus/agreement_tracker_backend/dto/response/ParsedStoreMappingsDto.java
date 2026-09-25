package com.medplus.agreement_tracker_backend.dto.response;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless store Excel parse result — no DB writes.
 */
public class ParsedStoreMappingsDto {

    private List<AgreementStoreMappingResponse> successfullyMapped = new ArrayList<>();
    private List<RowError> errors = new ArrayList<>();
    private int totalAttempted;

    public List<AgreementStoreMappingResponse> getSuccessfullyMapped() {
        return successfullyMapped;
    }

    public void setSuccessfullyMapped(List<AgreementStoreMappingResponse> successfullyMapped) {
        this.successfullyMapped = successfullyMapped;
    }

    public List<RowError> getErrors() {
        return errors;
    }

    public void setErrors(List<RowError> errors) {
        this.errors = errors;
    }

    public int getTotalAttempted() {
        return totalAttempted;
    }

    public void setTotalAttempted(int totalAttempted) {
        this.totalAttempted = totalAttempted;
    }

    public record RowError(Integer row, String storeCode, String message) {}
}
