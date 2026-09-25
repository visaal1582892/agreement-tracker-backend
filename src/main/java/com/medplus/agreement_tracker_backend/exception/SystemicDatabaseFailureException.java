package com.medplus.agreement_tracker_backend.exception;

public class SystemicDatabaseFailureException extends RuntimeException {
    public SystemicDatabaseFailureException(String message) {
        super(message);
    }
}
