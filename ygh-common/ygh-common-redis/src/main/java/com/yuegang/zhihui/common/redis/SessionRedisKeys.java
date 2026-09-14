package com.yuegang.zhihui.common.redis;

import java.util.Objects;

public final class SessionRedisKeys {
    private final RedisKeyBuilder keys;
    private final String environment;

    public SessionRedisKeys(RedisKeyBuilder keys, String environment) {
        this.keys = Objects.requireNonNull(keys, "keys must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        keys.build(environment, "auth", "session", "validation-probe");
    }

    public String session(long accountId, String jwtId) { return key(accountId, "session", jwtId); }
    public String revoked(long accountId, String jwtId) { return key(accountId, "revoked", jwtId); }
    public String accountState(long accountId) {
        return key(accountId, "account-state", "current");
    }

    private String key(long accountId, String business, String identifier) {
        if (accountId <= 0) throw new IllegalArgumentException("accountId must be positive");
        keys.build(environment, "auth", business, identifier);
        return "ygh:" + environment + ":auth:{" + accountId + "}:" + business + ':' + identifier;
    }
}
