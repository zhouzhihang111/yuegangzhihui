package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.auth.domain.AccountAccessState;
import com.yuegang.zhihui.auth.domain.AccountLockPolicy;
import com.yuegang.zhihui.auth.domain.AccountSecurityRepository;
import com.yuegang.zhihui.auth.domain.AccountSecuritySnapshot;
import com.yuegang.zhihui.auth.domain.AccountStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountLockServiceTest {

    @Test
    void successfulAuthenticationCannotClearLockCreatedByConcurrentFailure() {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        var beforeLock = new AccountAccessState(AccountStatus.ACTIVE, 4, Optional.empty());
        var concurrentLock = new AccountAccessState(
                AccountStatus.ACTIVE, 5, Optional.of(now.plus(Duration.ofMinutes(15))));
        var repository = new RacingRepository(beforeLock, concurrentLock);
        var service = new AccountLockService(
                repository, new AccountLockPolicy(5, Duration.ofMinutes(15)),
                Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.recordSuccess(1)).isEqualTo(concurrentLock);
        assertThat(repository.successfulWrites).isZero();
    }

    private static final class RacingRepository implements AccountSecurityRepository {
        private AccountSecuritySnapshot snapshot;
        private final AccountAccessState concurrentLock;
        private boolean raced;
        private int successfulWrites;

        private RacingRepository(AccountAccessState initial, AccountAccessState concurrentLock) {
            this.snapshot = new AccountSecuritySnapshot(1, initial, 0);
            this.concurrentLock = concurrentLock;
        }

        @Override
        public Optional<AccountSecuritySnapshot> findById(long accountId) {
            return Optional.of(snapshot);
        }

        @Override
        public boolean compareAndSetAccessState(long accountId, long expectedVersion, AccountAccessState newState) {
            if (!raced) {
                raced = true;
                snapshot = new AccountSecuritySnapshot(accountId, concurrentLock, 1);
                return false;
            }
            successfulWrites++;
            snapshot = new AccountSecuritySnapshot(accountId, newState, snapshot.version() + 1);
            return true;
        }
    }
}
