package com.yuegang.zhihui.common.mq;

import java.time.Instant;
import java.util.regex.Pattern;

/** Sanitized, auditable terminal consumption failure without exception messages or payload data. */
public record DeadLetterRecord(
        String eventId,
        String eventType,
        int eventVersion,
        String consumerGroup,
        String businessKey,
        String traceId,
        int deliveryAttempt,
        String failureCode,
        Instant failedAt
) {

    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern GROUP = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern FAILURE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    public DeadLetterRecord {
        requireText(eventId, "eventId", 128);
        if (eventType == null || !EVENT_TYPE.matcher(eventType).matches()) {
            throw new IllegalArgumentException("eventType is malformed");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be at least 1");
        }
        if (consumerGroup == null || !GROUP.matcher(consumerGroup).matches()) {
            throw new IllegalArgumentException("consumerGroup is malformed");
        }
        requireText(businessKey, "businessKey", 128);
        requireText(traceId, "traceId", 128);
        if (deliveryAttempt < 1) {
            throw new IllegalArgumentException("deliveryAttempt must be at least 1");
        }
        if (failureCode == null || !FAILURE_CODE.matcher(failureCode).matches()) {
            throw new IllegalArgumentException("failureCode must be a stable uppercase code");
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt must not be null");
        }
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is malformed");
        }
    }
}
