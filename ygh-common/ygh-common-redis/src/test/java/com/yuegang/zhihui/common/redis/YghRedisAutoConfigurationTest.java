package com.yuegang.zhihui.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class YghRedisAutoConfigurationTest {

    private final ApplicationContextRunner statelessRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(YghRedisAutoConfiguration.class));
    private final ApplicationContextRunner bootRedisRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataRedisAutoConfiguration.class, YghRedisAutoConfiguration.class));

    @Test
    void registersStatelessUtilitiesWithoutRedisConnection() {
        statelessRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisKeyBuilder.class);
            assertThat(context).hasSingleBean(TtlJitterPolicy.class);
            assertThat(context).doesNotHaveBean(RedisDistributedLock.class);
        });
    }

    @Test
    void registersLockOnlyWhenTemplateExists() {
        bootRedisRunner.withBean(
                        RedisConnectionFactory.class,
                        YghRedisAutoConfigurationTest::connectionFactoryStub)
                .run(context -> {
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                    assertThat(context).hasSingleBean(RedisLockCommands.class);
                    assertThat(context).hasSingleBean(LockOwnerTokenGenerator.class);
                    assertThat(context).hasSingleBean(RedisDistributedLock.class);
                });
    }

    private static RedisConnectionFactory connectionFactoryStub() {
        return (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toString" -> "RedisConnectionFactoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> method.getReturnType().equals(boolean.class) ? false : null;
                });
    }
}
