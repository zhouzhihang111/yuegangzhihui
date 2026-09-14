package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.domain.*;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class AccountLockService {
    private static final int MAX_CAS_ATTEMPTS = 16;

    private final AccountSecurityRepository repository;
    private final AccountLockPolicy policy;
    private final Clock clock;

    public AccountLockService(
            AccountSecurityRepository repository, AccountLockPolicy policy, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AccountAccessState recordFailure(long accountId) {
        return update(accountId, state -> policy.recordFailure(state, clock.instant()));
    }

    public AccountAccessState recordSuccess(long accountId) {
        return update(accountId, state -> policy.recordSuccess(state, clock.instant()));
    }

    public boolean authenticationAllowed(long accountId) {
        var snapshot = repository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("account not found"));
        return policy.authenticationAllowed(snapshot.accessState(), clock.instant());
    }

    private AccountAccessState update(long accountId, UnaryOperator<AccountAccessState> transition) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            var snapshot = repository.findById(accountId)
                    .orElseThrow(() -> new NoSuchElementException("account not found"));
            var updated = transition.apply(snapshot.accessState());
            if (updated.equals(snapshot.accessState())) {
                return updated;
            }
            if (repository.compareAndSetAccessState(
                    accountId, snapshot.version(), updated)) {
                return updated;
            }
        }
        throw new IllegalStateException("account security state contention exceeded retry limit");
    }
}
