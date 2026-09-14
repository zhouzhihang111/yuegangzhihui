package com.yuegang.zhihui.common.core;

import java.time.OffsetDateTime;

/** Identity of one caller operation and its canonical request body. */
public record IdempotencyRequestContext(
        String idempotencyKey,
        String operation,
        String subjectId,
        String requestFingerprint,
        OffsetDateTime requestedAt
) {

    public IdempotencyRequestContext {
        requireText(idempotencyKey, "idempotencyKey");
        requireText(operation, "operation");
        requireText(subjectId, "subjectId");
        requireText(requestFingerprint, "requestFingerprint");
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt must not be null");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
