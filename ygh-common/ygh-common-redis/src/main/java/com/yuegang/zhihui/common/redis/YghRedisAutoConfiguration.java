package com.yuegang.zhihui.common.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;

/** Shared Redis key, TTL and owner-safe short lock infrastructure. */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
public class YghRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisKeyBuilder redisKeyBuilder() {
        return new RedisKeyBuilder();
    }

    @Bean
    @ConditionalOnMissingBean
    public TtlJitterPolicy ttlJitterPolicy() {
        return new TtlJitterPolicy();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisLockCommands redisLockCommands(StringRedisTemplate redis) {
        return new SpringDataRedisLockCommands(redis);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public LockOwnerTokenGenerator lockOwnerTokenGenerator() {
        return new SecureLockOwnerTokenGenerator();
    }

    @Bean
    @ConditionalOnBean({StringRedisTemplate.class, RedisLockCommands.class})
    @ConditionalOnMissingBean
    public RedisDistributedLock redisDistributedLock(
            RedisLockCommands commands,
            RedisKeyBuilder keys,
            LockOwnerTokenGenerator ownerTokens
    ) {
        return new RedisDistributedLock(commands, keys, ownerTokens);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionRedisKeys sessionRedisKeys(
            RedisKeyBuilder keys,
            @Value("${ygh.redis.environment:dev}") String environment) {
        return new SessionRedisKeys(keys, environment);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisSessionStateStore redisSessionStateStore(StringRedisTemplate redis, SessionRedisKeys keys) {
        return new RedisSessionStateStore(redis, keys);
    }

    @Bean
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    @ConditionalOnMissingBean
    public ReactiveRedisSessionValidator reactiveRedisSessionValidator(
            ReactiveStringRedisTemplate redis, SessionRedisKeys keys) {
        return new ReactiveRedisSessionValidator(redis, keys);
    }
}
