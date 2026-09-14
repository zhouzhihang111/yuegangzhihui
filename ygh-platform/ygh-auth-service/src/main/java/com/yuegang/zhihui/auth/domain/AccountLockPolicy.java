package com.yuegang.zhihui.auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class AccountLockPolicy {
    private final int maximumFailures;
    private final Duration lockDuration;

    public AccountLockPolicy(int maximumFailures, Duration lockDuration) {
        if (maximumFailures < 1) {
            throw new IllegalArgumentException("maximumFailures must be positive");
        }
        this.lockDuration = Objects.requireNonNull(lockDuration, "lockDuration must not be null");
        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
        this.maximumFailures = maximumFailures;
    }

    public boolean authenticationAllowed(AccountAccessState state, Instant now) {
        Objects.requireNonNull(state, "state must not be null");
        return state.status() == AccountStatus.ACTIVE && !state.lockedAt(now);
    }

    public AccountAccessState recordFailure(AccountAccessState state, Instant now) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (state.status() != AccountStatus.ACTIVE || state.lockedAt(now)) {
            return state;
        }
        int priorFailures = state.lockedUntil().isPresent() ? 0 : state.failedLoginCount();
        int failures = Math.min(maximumFailures, priorFailures + 1);
        Optional<Instant> lockedUntil = failures >= maximumFailures
                ? Optional.of(now.plus(lockDuration)) : Optional.empty();
        return new AccountAccessState(state.status(), failures, lockedUntil);
    }

    public AccountAccessState recordSuccess(AccountAccessState state, Instant now) {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (state.status() != AccountStatus.ACTIVE || state.lockedAt(now)) {
            return state;
        }
        return AccountAccessState.active();
    }
}
