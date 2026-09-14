package com.yuegang.zhihui.common.redis;

import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

public final class ReactiveRedisSessionValidator implements ReactiveSessionValidator {
    private static final DefaultRedisScript<Long> VALIDATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 or redis.call('GET', KEYS[3]) ~= 'ACTIVE' then return 0 end
            local owner = redis.call('GET', KEYS[1])
            if owner and owner == ARGV[1] then return 1 end
            return 0
            """, Long.class);
    private final ReactiveStringRedisTemplate redis;
    private final SessionRedisKeys keys;

    public ReactiveRedisSessionValidator(ReactiveStringRedisTemplate redis, SessionRedisKeys keys) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.keys = Objects.requireNonNull(keys, "keys must not be null");
    }

    @Override
    public Mono<Boolean> valid(long accountId, String jwtId) {
        if (accountId <= 0) return Mono.just(false);
        if (jwtId == null || !jwtId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) return Mono.just(false);
        return redis.execute(VALIDATE_SCRIPT, List.of(
                        keys.session(accountId, jwtId), keys.revoked(accountId, jwtId), keys.accountState(accountId)),
                        List.of(Long.toString(accountId)))
                .singleOrEmpty().map(value -> value == 1L).defaultIfEmpty(false);
    }
}
