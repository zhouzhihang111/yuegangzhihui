package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import com.yuegang.zhihui.common.redis.SessionStateStore;

public final class TokenLifecycleUseCase {
    private final OpaqueRefreshTokenService refreshTokens;
    private final LoginAccountRepository accounts;
    private final AccessTokenIssuer accessTokens;
    private final Clock clock;
    private final SessionStateStore sessions;

    public TokenLifecycleUseCase(OpaqueRefreshTokenService refreshTokens, LoginAccountRepository accounts,
            AccessTokenIssuer accessTokens, SessionStateStore sessions, Clock clock) {
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.accounts = Objects.requireNonNull(accounts);
        this.accessTokens = Objects.requireNonNull(accessTokens);
        this.clock = Objects.requireNonNull(clock);
        this.sessions = Objects.requireNonNull(sessions);
    }

    public TokenResponse refresh(RefreshTokenRequest request) {
        char[] presented = request.refreshToken().toCharArray();
        try {
            var rotated = refreshTokens.rotate(presented);
            if (rotated.result().status() != RefreshRotationStatus.ROTATED) throw unauthenticated();
            RefreshTokenPair replacement = rotated.replacement();
            LoginAccount account = accounts.findByAccountId(rotated.result().accountId()).orElse(null);
            if (account == null || account.status() != AccountStatus.ACTIVE) {
                revoke(replacement, "REFRESH_ACCOUNT_INACTIVE");
                throw unauthenticated();
            }
            try {
                AccessToken access = accessTokens.issue(new TokenPrincipal(account.accountId(), account.userId(),
                        Set.of(account.accountType()), Set.of()));
                return new TokenResponse(access.value(), replacement.value(), "Bearer",
                        seconds(access.expiresAt()), seconds(replacement.expiresAt()));
            } catch (RuntimeException failure) {
                try { revoke(replacement, "REFRESH_COMPENSATION"); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        } finally { Arrays.fill(presented, '\0'); }
    }

    public OperationResponse logout(LogoutRequest request, AuthenticatedAccessToken access) {
        Objects.requireNonNull(access, "access must not be null");
        char[] token = request.refreshToken().toCharArray();
        RuntimeException refreshFailure = null;
        try { refreshTokens.revoke(token, "USER_LOGOUT"); }
        catch (RuntimeException failure) { refreshFailure = failure; }
        finally { Arrays.fill(token, '\0'); }
        try { sessions.revoke(access.accountId(), access.jwtId(), access.expiresAt(), clock.instant()); }
        catch (RuntimeException sessionFailure) {
            if (refreshFailure != null) refreshFailure.addSuppressed(sessionFailure);
            else throw sessionFailure;
        }
        if (refreshFailure != null) throw refreshFailure;
        return new OperationResponse(true);
    }

    private void revoke(RefreshTokenPair token, String reason) {
        char[] raw = token.value().toCharArray();
        try { refreshTokens.revoke(raw, reason); }
        finally { Arrays.fill(raw, '\0'); }
    }

    private long seconds(java.time.Instant expiry) { return Math.max(1, Duration.between(clock.instant(), expiry).toSeconds()); }
    private static BusinessException unauthenticated() { return new BusinessException(ErrorCode.UNAUTHENTICATED); }
}
