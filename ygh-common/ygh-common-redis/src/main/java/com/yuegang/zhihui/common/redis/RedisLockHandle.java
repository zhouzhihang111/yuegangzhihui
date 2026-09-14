package com.yuegang.zhihui.common.redis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/** Proof of ownership returned by a successful lock acquisition. */
public record RedisLockHandle(String key, @JsonIgnore String owner, Duration lease) {

    private static final Pattern OWNER = Pattern.compile("[A-Za-z0-9_-]{32,128}");

    public RedisLockHandle {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(lease, "lease must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (!OWNER.matcher(owner).matches()) {
            throw new IllegalArgumentException("owner token is malformed");
        }
        RedisDistributedLock.requireLease(lease);
    }

    @Override
    public String toString() {
        return "RedisLockHandle[key=" + key + ", owner=[REDACTED], lease=" + lease + ']';
    }
}
