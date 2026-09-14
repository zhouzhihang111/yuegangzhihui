package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.domain.AccessToken;
import com.yuegang.zhihui.auth.domain.AccessTokenIssuer;
import com.yuegang.zhihui.auth.domain.TokenPrincipal;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import java.time.Clock;
import java.util.Objects;

public final class SessionAwareAccessTokenIssuer implements AccessTokenIssuer {
    private final AccessTokenIssuer delegate;
    private final SessionStateStore sessions;
    private final Clock clock;

    public SessionAwareAccessTokenIssuer(
            AccessTokenIssuer delegate, SessionStateStore sessions, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public AccessToken issue(TokenPrincipal principal) {
        AccessToken token = delegate.issue(principal);
        sessions.register(principal.accountId(), token.jwtId(), token.expiresAt(), clock.instant());
        return token;
    }
}
