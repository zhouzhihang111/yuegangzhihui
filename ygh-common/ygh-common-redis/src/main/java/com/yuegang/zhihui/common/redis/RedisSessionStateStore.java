package com.yuegang.zhihui.common.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisSessionStateStore implements SessionStateStore {
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local existed = redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], '1', 'PX', ARGV[1])
            return existed
            """, Long.class);
    private final StringRedisTemplate redis;
    private final SessionRedisKeys keys;

    public RedisSessionStateStore(StringRedisTemplate redis, SessionRedisKeys keys) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.keys = Objects.requireNonNull(keys, "keys must not be null");
    }

    @Override
    public void register(long accountId, String jwtId, Instant expiresAt, Instant now) {
        if (accountId <= 0) throw new IllegalArgumentException("accountId must be positive");
        Duration ttl = positiveTtl(expiresAt, now);
        redis.opsForValue().setIfAbsent(keys.accountState(accountId), "ACTIVE");
        Boolean stored = redis.opsForValue().setIfAbsent(
                keys.session(accountId, jwtId), Long.toString(accountId), ttl);
        if (!Boolean.TRUE.equals(stored)) throw new IllegalStateException("JWT session id already exists");
    }

    @Override
    public void revoke(long accountId, String jwtId, Instant expiresAt, Instant now) {
        Duration ttl = positiveTtl(expiresAt, now);
        redis.execute(REVOKE_SCRIPT, List.of(keys.session(accountId, jwtId), keys.revoked(accountId, jwtId)),
                Long.toString(ttl.toMillis()));
    }

    @Override
    public void disableAccount(long accountId) {
        redis.opsForValue().set(keys.accountState(accountId), "DISABLED");
    }

    @Override
    public void enableAccount(long accountId) {
        redis.opsForValue().set(keys.accountState(accountId), "ACTIVE");
    }

    private Duration positiveTtl(Instant expiresAt, Instant now) {
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Duration ttl = Duration.between(now, expiresAt);
        requireBoundedTtl(ttl);
        return ttl;
    }

    private void requireBoundedTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("session TTL must be between 1ms and 31 days");
        }
    }
}
