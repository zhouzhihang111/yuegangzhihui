package com.yuegang.zhihui.common.core;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Uniform API response envelope shared by all HTTP services.
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp) {

    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.code(),
                ErrorCode.SUCCESS.defaultMessage(),
                data,
                traceId,
                OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message, String traceId) {
        return failure(errorCode, message, null, traceId);
    }

    public static <T> ApiResponse<T> failure(
            ErrorCode errorCode,
            String message,
            T data,
            String traceId
    ) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        var resolvedMessage = message == null || message.isBlank()
                ? errorCode.defaultMessage()
                : message;
        return new ApiResponse<>(errorCode.code(), resolvedMessage, data, traceId, OffsetDateTime.now());
    }
}
