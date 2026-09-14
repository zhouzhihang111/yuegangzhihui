package com.yuegang.zhihui.auth.infrastructure;

import com.yuegang.zhihui.auth.domain.CaptchaChallengeStore;
import com.yuegang.zhihui.common.redis.RedisKeyBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisCaptchaChallengeStore implements CaptchaChallengeStore {
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            local actual = redis.call('GET', KEYS[1])
            if not actual then return 0 end
            redis.call('DEL', KEYS[1])
            if actual == ARGV[1] then return 1 end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final RedisKeyBuilder keys;
    private final String environment;

    public RedisCaptchaChallengeStore(StringRedisTemplate redis, RedisKeyBuilder keys, String environment) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.keys = Objects.requireNonNull(keys, "keys must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    @Override public void save(String challengeId, String answerHash, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("captcha ttl must be between 1ms and 10 minutes");
        }
        Boolean created = redis.opsForValue().setIfAbsent(key(challengeId), answerHash, ttl);
        if (!Boolean.TRUE.equals(created)) throw new IllegalStateException("captcha challenge id collision");
    }

    @Override public boolean consume(String challengeId, String presentedAnswerHash) {
        return Long.valueOf(1L).equals(redis.execute(CONSUME, List.of(key(challengeId)), presentedAnswerHash));
    }

    private String key(String challengeId) { return keys.build(environment, "auth", "captcha", challengeId); }
}
