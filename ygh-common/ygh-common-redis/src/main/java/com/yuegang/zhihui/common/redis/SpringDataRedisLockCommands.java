package com.yuegang.zhihui.common.redis;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/** Spring Data Redis implementation using single-key atomic Lua operations. */
public final class SpringDataRedisLockCommands implements RedisLockCommands {

    static final RedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    static final RedisScript<Long> RENEW_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public SpringDataRedisLockCommands(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
    }

    @Override
    public boolean setIfAbsent(String key, String owner, Duration lease) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, lease));
    }

    @Override
    public boolean releaseIfOwner(String key, String owner) {
        Long result = redis.execute(RELEASE_IF_OWNER, List.of(key), owner);
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public boolean renewIfOwner(String key, String owner, Duration lease) {
        Long result = redis.execute(
                RENEW_IF_OWNER, List.of(key), owner, Long.toString(lease.toMillis()));
        return Long.valueOf(1L).equals(result);
    }
}
