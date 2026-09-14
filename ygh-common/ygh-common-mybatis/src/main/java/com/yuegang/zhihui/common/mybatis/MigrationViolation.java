package com.yuegang.zhihui.common.mybatis;

import java.util.Objects;

/** One deterministic problem found in a service migration catalog. */
public record MigrationViolation(
        MigrationViolationCode code,
        String resourcePath,
        String message
) {

    public MigrationViolation {
        Objects.requireNonNull(code, "code must not be null");
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath must not be blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }
}
