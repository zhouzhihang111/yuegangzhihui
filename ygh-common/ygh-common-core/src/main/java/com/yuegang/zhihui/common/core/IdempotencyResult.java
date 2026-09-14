package com.yuegang.zhihui.common.core;

import java.time.OffsetDateTime;

/** Result associated with an idempotency key and replayed after completion. */
public record IdempotencyResult<T>(
        String idempotencyKey,
        IdempotencyStatus status,
        T value,
        OffsetDateTime completedAt
) {

    public IdempotencyResult {
        requireText(idempotencyKey, "idempotencyKey");
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status == IdempotencyStatus.IN_PROGRESS && (value != null || completedAt != null)) {
            throw new IllegalArgumentException("in-progress result must not contain a replay value");
        }
        if (status == IdempotencyStatus.COMPLETED) {
            if (value == null) {
                throw new IllegalArgumentException("completed result value must not be null");
            }
            if (completedAt == null) {
                throw new IllegalArgumentException("completedAt must not be null");
            }
        }
    }

    public static <T> IdempotencyResult<T> inProgress(String idempotencyKey) {
        return new IdempotencyResult<>(idempotencyKey, IdempotencyStatus.IN_PROGRESS, null, null);
    }

    public static <T> IdempotencyResult<T> completed(
            String idempotencyKey,
            T value,
            OffsetDateTime completedAt
    ) {
        return new IdempotencyResult<>(idempotencyKey, IdempotencyStatus.COMPLETED, value, completedAt);
    }

    public boolean replayable() {
        return status == IdempotencyStatus.COMPLETED;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
