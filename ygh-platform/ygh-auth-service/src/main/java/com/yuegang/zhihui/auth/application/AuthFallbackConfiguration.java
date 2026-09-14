package com.yuegang.zhihui.auth.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "ygh.security.jwt", name = "enabled", havingValue = "false", matchIfMissing = true)
class AuthFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthCommandService.class)
    AuthCommandService contractOnlyAuthCommandService() {
        return new ContractOnlyAuthCommandService();
    }
}
