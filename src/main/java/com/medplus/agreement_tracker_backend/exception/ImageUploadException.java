package com.medplus.agreement_tracker_backend.exception;

/**
 * Upload failure with a message safe to return to the client UI.
 */
public class ImageUploadException extends RuntimeException {

    public ImageUploadException(String userMessage) {
        super(userMessage);
    }
}
