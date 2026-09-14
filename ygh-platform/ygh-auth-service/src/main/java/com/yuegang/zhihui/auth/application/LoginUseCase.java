package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.api.dto.AuthenticationResponse;
import com.yuegang.zhihui.auth.api.dto.LoginRequest;
import com.yuegang.zhihui.auth.api.dto.TokenResponse;
import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public final class LoginUseCase {
    private final LoginAccountRepository accounts;
    private final LoginRateLimiter rateLimiter;
    private final AccountLockService accountLocks;
    private final Argon2PasswordHasher passwordHasher;
    private final PasswordDigest dummyDigest;
    private final AccessTokenIssuer accessTokens;
    private final OpaqueRefreshTokenService refreshTokens;
    private final LoginAuditService audit;
    private final SessionStateStore sessions;
    private final Clock clock;

    public LoginUseCase(
            LoginAccountRepository accounts,
            LoginRateLimiter rateLimiter,
            AccountLockService accountLocks,
            Argon2PasswordHasher passwordHasher,
            PasswordDigest dummyDigest,
            AccessTokenIssuer accessTokens,
            OpaqueRefreshTokenService refreshTokens,
            LoginAuditService audit,
            SessionStateStore sessions,
            Clock clock
    ) {
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.accountLocks = Objects.requireNonNull(accountLocks, "accountLocks must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.dummyDigest = Objects.requireNonNull(dummyDigest, "dummyDigest must not be null");
        this.accessTokens = Objects.requireNonNull(accessTokens, "accessTokens must not be null");
        this.refreshTokens = Objects.requireNonNull(refreshTokens, "refreshTokens must not be null");
        this.audit = Objects.requireNonNull(audit, "audit must not be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AuthenticationResponse login(LoginRequest request, LoginSecurityContext context) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");
        String principal = PrincipalNormalizer.normalize(request.principal());
        LoginRateLimitDecision rate = rateLimiter.consume(principal, context.clientIp());
        if (!rate.allowed()) {
            audit.record(null, principal, context.clientIp(), LoginAttemptResult.RATE_LIMITED,
                    "RATE_LIMIT_" + rate.rejectedDimension().name(), context.traceId());
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        char[] rawPassword = request.password().toCharArray();
        try {
            LoginAccount account = accounts.findByPrincipal(principal).orElse(null);
            if (account == null) {
                passwordHasher.matches(rawPassword, dummyDigest);
                audit.record(null, principal, context.clientIp(), LoginAttemptResult.INVALID_CREDENTIALS,
                        "ACCOUNT_NOT_FOUND", context.traceId());
                throw unauthenticated();
            }

            boolean passwordMatches = passwordHasher.matches(rawPassword, account.passwordDigest());
            boolean authenticationAllowed = accountLocks.authenticationAllowed(account.accountId());
            if (!authenticationAllowed) {
                LoginAttemptResult result = account.status() == AccountStatus.DISABLED
                        ? LoginAttemptResult.ACCOUNT_DISABLED : LoginAttemptResult.ACCOUNT_LOCKED;
                audit.record(account.accountId(), principal, context.clientIp(), result,
                        result.name(), context.traceId());
                throw unauthenticated();
            }
            if (!passwordMatches) {
                accountLocks.recordFailure(account.accountId());
                audit.record(account.accountId(), principal, context.clientIp(),
                        LoginAttemptResult.INVALID_CREDENTIALS, "BAD_CREDENTIALS", context.traceId());
                throw unauthenticated();
            }

            accountLocks.recordSuccess(account.accountId());
            return issueAndAudit(account, principal, context);
        } finally {
            Arrays.fill(rawPassword, '\0');
        }
    }

    private AuthenticationResponse issueAndAudit(
            LoginAccount account, String principal, LoginSecurityContext context) {
        RefreshTokenPair refresh = null;
        AccessToken access = null;
        try {
            refresh = refreshTokens.issueInitial(account.accountId());
            access = accessTokens.issue(new TokenPrincipal(
                    account.accountId(), account.userId(), Set.of(account.accountType()), Set.of()));
            audit.record(account.accountId(), principal, context.clientIp(),
                    LoginAttemptResult.SUCCESS, null, context.traceId());
            long accessSeconds = positiveSeconds(access.expiresAt());
            long refreshSeconds = positiveSeconds(refresh.expiresAt());
            return new AuthenticationResponse(Long.toString(account.userId()),
                    new TokenResponse(access.value(), refresh.value(), "Bearer", accessSeconds, refreshSeconds));
        } catch (RuntimeException failure) {
            compensate(account.accountId(), refresh, access, failure);
            throw failure;
        }
    }

    private void compensate(long accountId, RefreshTokenPair refresh, AccessToken access, RuntimeException failure) {
        if (access != null) {
            try {
                sessions.revoke(accountId, access.jwtId(), access.expiresAt(), clock.instant());
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        if (refresh != null) {
            char[] value = refresh.value().toCharArray();
            try {
                refreshTokens.revoke(value, "LOGIN_COMPENSATION");
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            } finally {
                Arrays.fill(value, '\0');
            }
        }
    }

    private long positiveSeconds(java.time.Instant expiresAt) {
        return Math.max(1, Duration.between(clock.instant(), expiresAt).toSeconds());
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED);
    }
}
