package com.yuegang.zhihui.common.security;

import java.time.Instant;
import java.util.Objects;

/**
 * Credential-free security audit contract. Tokens, passwords and secrets are
 * intentionally absent from this model.
 */
public record SecurityAuditEvent(
        Instant occurredAt,
        String userId,
        String action,
        String requiredPermission,
        SecurityDecision decision,
        String reason,
        String traceId
) {

    public SecurityAuditEvent {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        requireText(userId, "userId");
        requireText(action, "action");
        requireText(requiredPermission, "requiredPermission");
        Objects.requireNonNull(decision, "decision must not be null");
        requireText(reason, "reason");
        requireText(traceId, "traceId");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
