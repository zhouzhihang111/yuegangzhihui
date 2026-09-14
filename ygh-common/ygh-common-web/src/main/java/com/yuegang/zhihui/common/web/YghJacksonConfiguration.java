package com.yuegang.zhihui.common.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Shared Jackson 3 configuration used by HTTP and message adapters. */
@Configuration(proxyBeanMethods = false)
public class YghJacksonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer yghJsonMapperBuilderCustomizer() {
        return YghJacksonConfiguration::customize;
    }

    /** Compatibility mapper for domain and MQ adapters not yet migrated to Jackson 3. */
    @Bean
    @ConditionalOnMissingBean(com.fasterxml.jackson.databind.ObjectMapper.class)
    com.fasterxml.jackson.databind.ObjectMapper legacyObjectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    }

    public static JsonMapper createMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        customize(builder);
        return builder.build();
    }

    private static void customize(JsonMapper.Builder builder) {
        builder.disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }
}
