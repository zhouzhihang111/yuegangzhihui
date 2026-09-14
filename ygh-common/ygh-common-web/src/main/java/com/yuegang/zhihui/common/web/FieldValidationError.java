package com.yuegang.zhihui.common.web;

/** Sanitized field validation detail safe for external API responses. */
public record FieldValidationError(String field, String message, Object rejectedValue) {

    public FieldValidationError {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        // Rejected input can contain passwords, tokens, addresses or personal data.
        rejectedValue = null;
    }

    public static FieldValidationError sanitized(String field, String message) {
        return new FieldValidationError(field, message, null);
    }
}
