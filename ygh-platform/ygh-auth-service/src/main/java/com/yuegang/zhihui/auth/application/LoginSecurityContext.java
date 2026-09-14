package com.yuegang.zhihui.auth.application;

import java.net.InetAddress;
import java.util.Objects;

public record LoginSecurityContext(InetAddress clientIp, String traceId) {
    public LoginSecurityContext {
        Objects.requireNonNull(clientIp, "clientIp must not be null");
        if (traceId == null || !traceId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("traceId is unsafe");
        }
    }
}
