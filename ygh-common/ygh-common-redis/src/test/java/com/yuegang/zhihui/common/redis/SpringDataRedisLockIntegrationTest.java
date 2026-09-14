package com.yuegang.zhihui.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

class SpringDataRedisLockIntegrationTest {

    private static final GenericContainer<?> REDIS = YghTestContainerFactory.redis();

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;
    private static SpringDataRedisLockCommands commands;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        commands = new SpringDataRedisLockCommands(template);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @Test
    void setNxPxAndOwnerCheckedRenewAndReleaseRunAgainstRedis() {
        String key = key("owner");
        String owner = owner();
        String other = owner();

        assertThat(commands.setIfAbsent(key, owner, Duration.ofSeconds(5))).isTrue();
        assertThat(commands.setIfAbsent(key, other, Duration.ofSeconds(5))).isFalse();
        assertThat(commands.renewIfOwner(key, other, Duration.ofSeconds(10))).isFalse();
        assertThat(commands.renewIfOwner(key, owner, Duration.ofSeconds(10))).isTrue();
        assertThat(commands.releaseIfOwner(key, other)).isFalse();
        assertThat(commands.releaseIfOwner(key, owner)).isTrue();
        assertThat(template.hasKey(key)).isFalse();
    }

    @Test
    void expiredOwnerCannotDeleteLockReacquiredByNewOwner() throws InterruptedException {
        String key = key("expiry");
        String expiredOwner = owner();
        String newOwner = owner();

        assertThat(commands.setIfAbsent(key, expiredOwner, Duration.ofSeconds(1))).isTrue();
        awaitKeyExpiry(key, Duration.ofSeconds(5));
        assertThat(commands.setIfAbsent(key, newOwner, Duration.ofSeconds(5))).isTrue();
        assertThat(commands.releaseIfOwner(key, expiredOwner)).isFalse();
        assertThat(template.opsForValue().get(key)).isEqualTo(newOwner);
        assertThat(commands.releaseIfOwner(key, newOwner)).isTrue();
    }

    @Test
    void concurrentAcquisitionHasExactlyOneWinner() throws Exception {
        String key = key("concurrent");
        int contenders = 16;
        var ready = new CountDownLatch(contenders);
        var start = new CountDownLatch(1);
        var results = new ArrayList<java.util.concurrent.Future<Boolean>>(contenders);

        try (var executor = Executors.newFixedThreadPool(contenders)) {
            for (int index = 0; index < contenders; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return commands.setIfAbsent(key, owner(), Duration.ofSeconds(5));
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long winners = 0L;
            for (var result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1L);
        } finally {
            template.delete(key);
        }
    }

    private static void awaitKeyExpiry(String key, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (Boolean.TRUE.equals(template.hasKey(key)) && System.nanoTime() < deadline) {
            Thread.sleep(25L);
        }
        assertThat(template.hasKey(key)).isFalse();
    }

    private static String key(String business) {
        return "ygh:test:common-redis:" + business + ':' + UUID.randomUUID();
    }

    private static String owner() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
