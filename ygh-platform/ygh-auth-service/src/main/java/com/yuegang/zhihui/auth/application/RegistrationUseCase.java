package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public final class RegistrationUseCase {
    private final LoginAccountRepository accounts;
    private final PasswordPolicy passwords;
    private final Argon2PasswordHasher passwordHasher;
    private final CaptchaService captchas;
    private final SnowflakeIdGenerator ids;
    private final AccessTokenIssuer accessTokens;
    private final OpaqueRefreshTokenService refreshTokens;
    private final SessionStateStore sessions;
    private final Clock clock;

    public RegistrationUseCase(LoginAccountRepository accounts, PasswordPolicy passwords,
            Argon2PasswordHasher passwordHasher, CaptchaService captchas, SnowflakeIdGenerator ids,
            AccessTokenIssuer accessTokens, OpaqueRefreshTokenService refreshTokens,
            SessionStateStore sessions, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts);
        this.passwords = Objects.requireNonNull(passwords);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.captchas = Objects.requireNonNull(captchas);
        this.ids = Objects.requireNonNull(ids);
        this.accessTokens = Objects.requireNonNull(accessTokens);
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.sessions = Objects.requireNonNull(sessions);
        this.clock = Objects.requireNonNull(clock);
    }

    public AuthenticationResponse register(RegisterRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        captchas.verify(request.captchaChallengeId(), request.captchaAnswer());
        String principal = PrincipalNormalizer.normalize(request.principal());
        char[] raw = request.password().toCharArray();
        try {
            if (!passwords.validate(raw).valid()) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            if (accounts.findByPrincipal(principal).isPresent()) throw conflict();
            long accountId = ids.nextId();
            long userId = ids.nextId();
            LoginAccount account;
            try {
                account = accounts.create(accountId, userId, principal, "CUSTOMER", passwordHasher.hash(raw));
            } catch (AccountAlreadyExistsException duplicate) {
                throw conflict();
            }
            return issue(account);
        } finally { Arrays.fill(raw, '\0'); }
    }

    private AuthenticationResponse issue(LoginAccount account) {
        RefreshTokenPair refresh = null;
        AccessToken access = null;
        try {
            refresh = refreshTokens.issueInitial(account.accountId());
            access = accessTokens.issue(new TokenPrincipal(account.accountId(), account.userId(),
                    Set.of(account.accountType()), Set.of()));
            return new AuthenticationResponse(Long.toString(account.userId()), new TokenResponse(
                    access.value(), refresh.value(), "Bearer", seconds(access.expiresAt()), seconds(refresh.expiresAt())));
        } catch (RuntimeException failure) {
            if (access != null) {
                try { sessions.revoke(account.accountId(), access.jwtId(), access.expiresAt(), clock.instant()); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            if (refresh != null) {
                char[] token = refresh.value().toCharArray();
                try { refreshTokens.revoke(token, "REGISTRATION_COMPENSATION"); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                finally { Arrays.fill(token, '\0'); }
            }
            throw failure;
        }
    }

    private long seconds(java.time.Instant expiry) { return Math.max(1, Duration.between(clock.instant(), expiry).toSeconds()); }
    private static BusinessException conflict() { return new BusinessException(ErrorCode.BUSINESS_CONFLICT); }
}
