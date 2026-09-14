package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.auth.api.dto.LoginRequest;
import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LoginUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final char[] VALID_PASSWORD = "Correct Horse Battery 2026".toCharArray();

    @Test
    void successfulLoginResetsFailuresAuditsAndReturnsRedactedTokens() throws Exception {
        Fixture fixture = new Fixture(false);

        var response = fixture.useCase.login(request(new String(VALID_PASSWORD)), context());

        assertThat(response.userId()).isEqualTo("84");
        assertThat(response.tokens().accessToken()).isEqualTo("access-secret");
        assertThat(response.tokens().refreshToken()).isNotBlank();
        assertThat(response.tokens().expiresIn()).isEqualTo(900);
        assertThat(fixture.security.state.failedLoginCount()).isZero();
        assertThat(fixture.audits).singleElement()
                .extracting(LoginAttempt::result).isEqualTo(LoginAttemptResult.SUCCESS);
        assertThat(response.toString()).doesNotContain("access-secret", response.tokens().refreshToken());
    }

    @Test
    void unknownAndWrongPrincipalReturnSameExternalErrorAndAreAudited() throws Exception {
        Fixture unknown = new Fixture(false);
        unknown.account = null;
        Fixture wrong = new Fixture(false);

        assertUnauthenticated(() -> unknown.useCase.login(request("not the password"), context()));
        assertUnauthenticated(() -> wrong.useCase.login(request("not the password"), context()));

        assertThat(unknown.audits).singleElement().satisfies(attempt -> {
            assertThat(attempt.accountId()).isNull();
            assertThat(attempt.failureReason()).isEqualTo("ACCOUNT_NOT_FOUND");
        });
        assertThat(wrong.audits).singleElement().satisfies(attempt -> {
            assertThat(attempt.accountId()).isEqualTo(42L);
            assertThat(attempt.failureReason()).isEqualTo("BAD_CREDENTIALS");
        });
    }

    @Test
    void auditFailureRevokesBothIssuedTokenFamiliesBeforeFailingClosed() throws Exception {
        Fixture fixture = new Fixture(true);

        assertThatThrownBy(() -> fixture.useCase.login(request(new String(VALID_PASSWORD)), context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");

        assertThat(fixture.sessions.revocations).isEqualTo(1);
        assertThat(fixture.refreshRepository.revocations.get()).isEqualTo(1);
    }

    private static LoginRequest request(String password) {
        return new LoginRequest("Alice@Example.com", password, null, null, "device-1");
    }

    private static LoginSecurityContext context() throws Exception {
        return new LoginSecurityContext(InetAddress.getByName("192.0.2.8"), "trace-login");
    }

    private static void assertUnauthenticated(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    private static final class Fixture {
        private final Argon2PasswordHasher hasher = Argon2PasswordHasher.owaspMinimum();
        private final MutableSecurityRepository security = new MutableSecurityRepository();
        private final ArrayList<LoginAttempt> audits = new ArrayList<>();
        private final FakeRefreshRepository refreshRepository = new FakeRefreshRepository();
        private final FakeSessions sessions = new FakeSessions();
        private LoginAccount account;
        private final LoginUseCase useCase;

        private Fixture(boolean failAudit) {
            PasswordDigest actual = hasher.hash(VALID_PASSWORD.clone());
            PasswordDigest dummy = hasher.hash("dummy password value 2026".toCharArray());
            account = new LoginAccount(42, 84, "CUSTOMER", AccountStatus.ACTIVE, actual);
            LoginAttemptRepository auditRepository = attempt -> {
                if (failAudit) throw new IllegalStateException("audit unavailable");
                audits.add(attempt);
            };
            var audit = new LoginAuditService(
                    auditRepository,
                    new SensitiveValueHasher(
                            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)),
                    CLOCK);
            var refresh = new OpaqueRefreshTokenService(refreshRepository, CLOCK, Duration.ofDays(14));
            useCase = new LoginUseCase(
                    principal -> Optional.ofNullable(account),
                    (principal, ip) -> LoginRateLimitDecision.allow(),
                    new AccountLockService(security, new AccountLockPolicy(5, Duration.ofMinutes(15)), CLOCK),
                    hasher,
                    dummy,
                    principal -> new AccessToken("access-secret", "jti-1", NOW.plusSeconds(900)),
                    refresh,
                    audit,
                    sessions,
                    CLOCK);
        }
    }

    private static final class MutableSecurityRepository implements AccountSecurityRepository {
        private AccountAccessState state = new AccountAccessState(
                AccountStatus.ACTIVE, 2, Optional.empty());
        private long version;

        @Override
        public Optional<AccountSecuritySnapshot> findById(long accountId) {
            return Optional.of(new AccountSecuritySnapshot(accountId, state, version));
        }

        @Override
        public boolean compareAndSetAccessState(
                long accountId, long expectedVersion, AccountAccessState newState) {
            if (expectedVersion != version) return false;
            state = newState;
            version++;
            return true;
        }
    }

    private static final class FakeRefreshRepository implements RefreshTokenRepository {
        private final AtomicInteger revocations = new AtomicInteger();

        @Override public void insertInitial(long accountId, String family, NewRefreshToken token) {}
        @Override public RefreshRotationResult rotate(String hash, NewRefreshToken replacement, Instant now) {
            return RefreshRotationResult.invalid();
        }
        @Override public void revokeFamilyByTokenHash(String hash, Instant now, String reason) {
            revocations.incrementAndGet();
        }
    }

    private static final class FakeSessions implements SessionStateStore {
        private int revocations;
        @Override public void register(long accountId, String jwtId, Instant expiresAt, Instant now) {}
        @Override public void revoke(long accountId, String jwtId, Instant expiresAt, Instant now) { revocations++; }
        @Override public void disableAccount(long accountId) {}
        @Override public void enableAccount(long accountId) {}
    }
}
