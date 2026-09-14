package com.yuegang.zhihui.auth.domain;

import java.time.Instant;
import java.util.Objects;

public record AccessToken(String value, String jwtId, Instant expiresAt) {
    public AccessToken {
        if (Objects.requireNonNull(value, "value must not be null").isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (!Objects.requireNonNull(jwtId, "jwtId must not be null")
                .matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("jwtId is unsafe");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override public String toString() {
        return "AccessToken[value=[REDACTED], jwtId=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
