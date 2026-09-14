package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.domain.LoginAttempt;
import com.yuegang.zhihui.auth.domain.LoginAttemptRepository;
import com.yuegang.zhihui.auth.domain.LoginAttemptResult;
import com.yuegang.zhihui.auth.domain.SensitiveValueHasher;
import java.net.InetAddress;
import java.time.Clock;
import java.util.Objects;

public final class LoginAuditService {
    private final LoginAttemptRepository repository;
    private final SensitiveValueHasher hasher;
    private final Clock clock;

    public LoginAuditService(LoginAttemptRepository repository, SensitiveValueHasher hasher, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void record(
            Long accountId,
            String principal,
            InetAddress clientIp,
            LoginAttemptResult result,
            String failureReason,
            String traceId
    ) {
        Objects.requireNonNull(clientIp, "clientIp must not be null");
        repository.save(new LoginAttempt(
                accountId,
                hasher.hashPrincipal(principal),
                hasher.hashClientAddress(clientIp),
                result,
                failureReason,
                clock.instant(),
                traceId));
    }
}
