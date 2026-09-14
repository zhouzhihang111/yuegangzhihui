package com.yuegang.zhihui.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.testcontainers.containers.GenericContainer;

class RedisSessionStateIntegrationTest {
    private static final GenericContainer<?> REDIS = YghTestContainerFactory.redis();
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate sync;
    private static RedisSessionStateStore store;
    private static ReactiveRedisSessionValidator validator;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        sync = new StringRedisTemplate(connectionFactory);
        sync.afterPropertiesSet();
        var keys = new SessionRedisKeys(new RedisKeyBuilder(), "test");
        store = new RedisSessionStateStore(sync, keys);
        validator = new ReactiveRedisSessionValidator(
                new ReactiveStringRedisTemplate(connectionFactory, RedisSerializationContext.string()), keys);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    void sessionRevocationAndAccountDisableFailClosedAtomically() {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        String first = jwtId();
        store.register(42, first, now.plusSeconds(30), now);
        assertThat(validator.valid(42, first).block(Duration.ofSeconds(3))).isTrue();
        assertThat(validator.valid(43, first).block(Duration.ofSeconds(3))).isFalse();

        store.revoke(42, first, now.plusSeconds(30), now);
        assertThat(validator.valid(42, first).block(Duration.ofSeconds(3))).isFalse();

        String second = jwtId();
        store.register(42, second, now.plusSeconds(30), now);
        store.disableAccount(42);
        assertThat(validator.valid(42, second).block(Duration.ofSeconds(3))).isFalse();
        store.enableAccount(42);
        assertThat(validator.valid(42, second).block(Duration.ofSeconds(3))).isTrue();

        sync.delete(new SessionRedisKeys(new RedisKeyBuilder(), "test").accountState(42));
        assertThat(validator.valid(42, second).block(Duration.ofSeconds(3)))
                .as("missing account security state must fail closed")
                .isFalse();
    }

    @Test
    void duplicateJtiAndUnboundedTtlAreRejected() {
        Instant now = Instant.now();
        String jwtId = jwtId();
        store.register(1, jwtId, now.plusSeconds(5), now);
        assertThatThrownBy(() -> store.register(1, jwtId, now.plusSeconds(5), now))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.disableAccount(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(validator.valid(1, "unsafe:jti").block(Duration.ofSeconds(3))).isFalse();
    }

    private static String jwtId() { return UUID.randomUUID().toString(); }
}
