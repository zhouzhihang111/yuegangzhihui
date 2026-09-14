package com.yuegang.zhihui.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.auth.domain.*;
import com.yuegang.zhihui.common.redis.RedisKeyBuilder;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

class RedisLoginRateLimiterIntegrationTest {
    private static final GenericContainer<?> REDIS = YghTestContainerFactory.redis();
    private static LettuceConnectionFactory connectionFactory;
    private static RedisLoginRateLimiter limiter;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        var template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        limiter = new RedisLoginRateLimiter(
                template,
                new RedisKeyBuilder(),
                new SensitiveValueHasher(
                        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)),
                new LoginRateLimitPolicy(2, Duration.ofMinutes(15), 3, Duration.ofMinutes(15)),
                "test");
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    void principalAndIpDimensionsAreConsumedAndRejectedIndependently() throws Exception {
        assertThat(limiter.consume("alice@example.com", ip("192.0.2.1")).allowed()).isTrue();
        assertThat(limiter.consume("alice@example.com", ip("192.0.2.2")).allowed()).isTrue();

        LoginRateLimitDecision principalBlocked =
                limiter.consume("alice@example.com", ip("192.0.2.3"));
        assertThat(principalBlocked.allowed()).isFalse();
        assertThat(principalBlocked.rejectedDimension()).isEqualTo(LoginRateLimitDimension.PRINCIPAL);
        assertThat(principalBlocked.retryAfter()).isBetween(Duration.ofMinutes(14), Duration.ofMinutes(15));

        assertThat(limiter.consume("bob@example.com", ip("192.0.2.1")).allowed()).isTrue();
        assertThat(limiter.consume("carol@example.com", ip("192.0.2.1")).allowed()).isTrue();
        LoginRateLimitDecision ipBlocked = limiter.consume("dave@example.com", ip("192.0.2.1"));
        assertThat(ipBlocked.allowed()).isFalse();
        assertThat(ipBlocked.rejectedDimension()).isEqualTo(LoginRateLimitDimension.IP);
    }

    private static InetAddress ip(String value) throws Exception {
        return InetAddress.getByName(value);
    }
}
