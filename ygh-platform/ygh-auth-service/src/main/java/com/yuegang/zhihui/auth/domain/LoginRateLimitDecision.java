package com.yuegang.zhihui.auth.domain;

import java.time.Duration;
import java.util.Objects;

public record LoginRateLimitDecision(
        boolean allowed,
        LoginRateLimitDimension rejectedDimension,
        Duration retryAfter
) {
    public LoginRateLimitDecision {
        Objects.requireNonNull(rejectedDimension, "rejectedDimension must not be null");
        Objects.requireNonNull(retryAfter, "retryAfter must not be null");
        if (retryAfter.isNegative() || (allowed && !retryAfter.isZero())
                || (allowed && rejectedDimension != LoginRateLimitDimension.NONE)
                || (!allowed && (retryAfter.isZero() || rejectedDimension == LoginRateLimitDimension.NONE))) {
            throw new IllegalArgumentException("rate-limit decision is inconsistent");
        }
    }

    public static LoginRateLimitDecision allow() {
        return new LoginRateLimitDecision(true, LoginRateLimitDimension.NONE, Duration.ZERO);
    }
}
