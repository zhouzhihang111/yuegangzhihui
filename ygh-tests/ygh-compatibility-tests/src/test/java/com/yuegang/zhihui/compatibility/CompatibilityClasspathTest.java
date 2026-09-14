package com.yuegang.zhihui.compatibility;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CompatibilityClasspathTest {

    @ParameterizedTest(name = "loads {0}")
    @MethodSource("representativeConfigurationClasses")
    void representativeAutoConfigurationClassesLinkOnJdk25(String className) {
        var classLoader = Thread.currentThread().getContextClassLoader();

        assertThatCode(() -> Class.forName(className, false, classLoader))
                .as("The managed dependency set must link %s", className)
                .doesNotThrowAnyException();
    }

    static Stream<String> representativeConfigurationClasses() {
        return Stream.of(
                "org.springframework.boot.SpringApplication",
                "org.springframework.cloud.openfeign.FeignAutoConfiguration",
                "com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration",
                "com.alibaba.cloud.nacos.NacosConfigSpringCloudAutoConfiguration",
                "com.alibaba.cloud.sentinel.custom.SentinelAutoConfiguration",
                "com.alibaba.cloud.stream.binder.rocketmq.autoconfigurate.RocketMQBinderAutoConfiguration",
                "com.alibaba.cloud.seata.feign.SeataFeignClientAutoConfiguration",
                "org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration");
    }
}
