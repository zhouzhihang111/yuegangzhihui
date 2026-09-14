package com.yuegang.zhihui.auth.domain;

import java.util.Objects;

public record LoginAccount(
        long accountId,
        long userId,
        String accountType,
        AccountStatus status,
        PasswordDigest passwordDigest
) {
    public LoginAccount {
        if (accountId <= 0 || userId <= 0) throw new IllegalArgumentException("account identifiers must be positive");
        if (accountType == null || !accountType.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new IllegalArgumentException("accountType is unsafe");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(passwordDigest, "passwordDigest must not be null");
    }
}
