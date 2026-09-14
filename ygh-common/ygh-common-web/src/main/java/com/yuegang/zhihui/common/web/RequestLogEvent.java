package com.yuegang.zhihui.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

/** Sanitized request metadata. Query strings, bodies and credentials are excluded. */
public record RequestLogEvent(
        String traceId,
        String requestId,
        String method,
        String path,
        int status,
        long durationMs,
        Map<String, String> headers
) {

    public RequestLogEvent {
        requireText(traceId, "traceId");
        requireText(requestId, "requestId");
        requireText(method, "method");
        requireText(path, "path");
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be a valid HTTP status");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (headers == null) {
            throw new IllegalArgumentException("headers must not be null");
        }
        headers = Map.copyOf(new LinkedHashMap<>(headers));
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
