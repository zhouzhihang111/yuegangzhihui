package com.yuegang.zhihui.auth.domain;

import java.time.Instant;
import java.util.Objects;

public record NewRefreshToken(long id, String tokenHash, Instant issuedAt, Instant expiresAt) {
    public NewRefreshToken {
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        if (!Objects.requireNonNull(tokenHash, "tokenHash must not be null").matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("tokenHash must be a SHA-256 hex digest");
        }
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        if (!Objects.requireNonNull(expiresAt, "expiresAt must not be null").isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }
}
