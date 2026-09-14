package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AuthFallbackConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthFallbackConfiguration.class);

    @Test
    void suppliesFailClosedFallbackOnlyWhenNoRealUseCaseExists() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(AuthCommandService.class)
                .hasBean("contractOnlyAuthCommandService"));

        runner.withPropertyValues("ygh.security.jwt.enabled=true")
                .withUserConfiguration(RealServiceConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(AuthCommandService.class);
            assertThat(context).doesNotHaveBean("contractOnlyAuthCommandService");
            assertThat(context.getBean(AuthCommandService.class)).isSameAs(REAL_SERVICE);
                });
    }

    private static final AuthCommandService REAL_SERVICE = new ContractOnlyAuthCommandService();

    @Configuration(proxyBeanMethods = false)
    static class RealServiceConfiguration {
        @Bean AuthCommandService realAuthCommandService() { return REAL_SERVICE; }
    }
}
