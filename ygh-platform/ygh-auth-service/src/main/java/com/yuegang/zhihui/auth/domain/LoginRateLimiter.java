package com.yuegang.zhihui.auth.domain;

import java.net.InetAddress;

@FunctionalInterface
public interface LoginRateLimiter {
    LoginRateLimitDecision consume(String principal, InetAddress clientIp);
}
