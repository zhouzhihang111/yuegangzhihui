package com.yuegang.zhihui.auth.application;

import java.time.Instant;

public record AuthenticatedAccessToken(long accountId, String jwtId, Instant expiresAt) {
    public AuthenticatedAccessToken {
        if (accountId <= 0) throw new IllegalArgumentException("accountId must be positive");
        if (jwtId == null || !jwtId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException("jwtId is unsafe");
        }
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt must not be null");
    }
}
