package com.yuegang.zhihui.auth.domain;

import java.time.Duration;

public record LoginRateLimitPolicy(
        int principalLimit,
        Duration principalWindow,
        int ipLimit,
        Duration ipWindow
) {
    private static final int MAXIMUM_LIMIT = 10_000;
    private static final Duration MAXIMUM_WINDOW = Duration.ofDays(1);

    public LoginRateLimitPolicy {
        validate(principalLimit, principalWindow, "principal");
        validate(ipLimit, ipWindow, "ip");
    }

    public static LoginRateLimitPolicy enterpriseDefault() {
        return new LoginRateLimitPolicy(10, Duration.ofMinutes(15), 30, Duration.ofMinutes(15));
    }

    private static void validate(int limit, Duration window, String dimension) {
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException(dimension + " limit must be between 1 and 10000");
        }
        if (window == null || window.compareTo(Duration.ofMillis(1)) < 0
                || window.compareTo(MAXIMUM_WINDOW) > 0) {
            throw new IllegalArgumentException(dimension + " window must be between 1ms and 1 day");
        }
    }
}
