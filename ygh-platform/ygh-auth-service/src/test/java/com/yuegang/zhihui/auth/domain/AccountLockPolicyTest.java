package com.yuegang.zhihui.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountLockPolicyTest {

    private final AccountLockPolicy policy = new AccountLockPolicy(5, Duration.ofMinutes(15));
    private final Instant now = Instant.parse("2026-07-12T00:00:00Z");

    @Test
    void fifthConsecutiveFailureLocksForTheConfiguredWindow() {
        AccountAccessState state = AccountAccessState.active();
        for (int attempt = 1; attempt <= 4; attempt++) {
            state = policy.recordFailure(state, now.plusSeconds(attempt));
            assertThat(state.lockedAt(now.plusSeconds(attempt))).isFalse();
            assertThat(state.failedLoginCount()).isEqualTo(attempt);
        }

        state = policy.recordFailure(state, now.plusSeconds(5));
        assertThat(state.failedLoginCount()).isEqualTo(5);
        assertThat(state.lockedUntil()).hasValue(now.plusSeconds(5).plus(Duration.ofMinutes(15)));
        assertThat(state.lockedAt(now.plusSeconds(6))).isTrue();
    }

    @Test
    void successAndExpiredLockResetFailuresWhileDisabledAccountsStayDenied() {
        var failed = new AccountAccessState(AccountStatus.ACTIVE, 3, java.util.Optional.empty());
        assertThat(policy.recordSuccess(failed, now).failedLoginCount()).isZero();

        var locked = new AccountAccessState(
                AccountStatus.ACTIVE, 5, java.util.Optional.of(now.plusSeconds(60)));
        assertThat(policy.recordSuccess(locked, now)).isEqualTo(locked);

        var expired = new AccountAccessState(
                AccountStatus.ACTIVE, 5, java.util.Optional.of(now.minusSeconds(1)));
        var afterFailure = policy.recordFailure(expired, now);
        assertThat(afterFailure.failedLoginCount()).isEqualTo(1);
        assertThat(afterFailure.lockedUntil()).isEmpty();

        var disabled = new AccountAccessState(AccountStatus.DISABLED, 0, java.util.Optional.empty());
        assertThat(policy.authenticationAllowed(disabled, now)).isFalse();
        assertThat(policy.recordFailure(disabled, now)).isEqualTo(disabled);
    }
}
