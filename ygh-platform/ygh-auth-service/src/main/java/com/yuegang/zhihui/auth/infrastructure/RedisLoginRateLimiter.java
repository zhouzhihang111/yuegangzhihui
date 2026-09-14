package com.yuegang.zhihui.auth.infrastructure;

import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.redis.RedisKeyBuilder;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Fixed-window limiter. Each dimension is atomic and both dimensions are always consumed. */
public final class RedisLoginRateLimiter implements LoginRateLimiter {
    private static final DefaultRedisScript<List> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
              ttl = tonumber(ARGV[2])
            end
            if count > tonumber(ARGV[1]) then return {0, ttl, count} end
            return {1, ttl, count}
            """, List.class);

    private final StringRedisTemplate redis;
    private final RedisKeyBuilder keys;
    private final SensitiveValueHasher hasher;
    private final LoginRateLimitPolicy policy;
    private final String environment;

    public RedisLoginRateLimiter(
            StringRedisTemplate redis,
            RedisKeyBuilder keys,
            SensitiveValueHasher hasher,
            LoginRateLimitPolicy policy,
            String environment
    ) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.keys = Objects.requireNonNull(keys, "keys must not be null");
        this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        keys.build(environment, "auth", "login-principal", "validation");
    }

    @Override
    public LoginRateLimitDecision consume(String principal, InetAddress clientIp) {
        Objects.requireNonNull(clientIp, "clientIp must not be null");
        Result principalResult = consumeOne(
                keys.build(environment, "auth", "login-principal", hasher.hashPrincipal(principal)),
                policy.principalLimit(), policy.principalWindow());
        Result ipResult = consumeOne(
                keys.build(environment, "auth", "login-ip", hasher.hashClientAddress(clientIp)),
                policy.ipLimit(), policy.ipWindow());
        if (principalResult.allowed && ipResult.allowed) return LoginRateLimitDecision.allow();
        LoginRateLimitDimension dimension = !principalResult.allowed && !ipResult.allowed
                ? LoginRateLimitDimension.BOTH
                : principalResult.allowed ? LoginRateLimitDimension.IP : LoginRateLimitDimension.PRINCIPAL;
        Duration retryAfter = switch (dimension) {
            case PRINCIPAL -> principalResult.retryAfter;
            case IP -> ipResult.retryAfter;
            case BOTH -> principalResult.retryAfter.compareTo(ipResult.retryAfter) >= 0
                    ? principalResult.retryAfter : ipResult.retryAfter;
            case NONE -> throw new IllegalStateException("rejected rate-limit decision has no dimension");
        };
        return new LoginRateLimitDecision(false, dimension, retryAfter);
    }

    private Result consumeOne(String key, int limit, Duration window) {
        List<?> raw = redis.execute(CONSUME_SCRIPT, List.of(key),
                Integer.toString(limit), Long.toString(window.toMillis()));
        if (raw == null || raw.size() != 3 || !(raw.get(0) instanceof Number allowed)
                || !(raw.get(1) instanceof Number ttl)) {
            throw new AccountSecurityPersistenceException("login rate-limit response is invalid", null);
        }
        return new Result(allowed.longValue() == 1, Duration.ofMillis(Math.max(1, ttl.longValue())));
    }

    private record Result(boolean allowed, Duration retryAfter) {}
}
