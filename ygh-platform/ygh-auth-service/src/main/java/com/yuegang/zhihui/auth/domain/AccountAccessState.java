package com.yuegang.zhihui.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AccountAccessState(
        AccountStatus status,
        int failedLoginCount,
        Optional<Instant> lockedUntil) {
    public AccountAccessState {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(lockedUntil, "lockedUntil must not be null");
        if (failedLoginCount < 0) {
            throw new IllegalArgumentException("failedLoginCount must not be negative");
        }
    }

    public static AccountAccessState active() {
        return new AccountAccessState(AccountStatus.ACTIVE, 0, Optional.empty());
    }

    public boolean lockedAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return lockedUntil.filter(until -> until.isAfter(now)).isPresent();
    }
}
