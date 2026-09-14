package com.yuegang.zhihui.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Servlet request-logging auto-configuration for services importing common-web. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RequestLogSink.class)
    RequestLogSink requestLogSink() {
        return new StructuredRequestLogSink();
    }

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(RequestLoggingFilter.class)
    RequestLoggingFilter requestLoggingFilter(RequestLogSink sink) {
        return new RequestLoggingFilter(sink);
    }

    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(
            RequestLoggingFilter filter
    ) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setName("yghRequestLoggingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setEnabled(true);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(AuditLoggingFilter.class)
    AuditLoggingFilter auditLoggingFilter() {
        return new AuditLoggingFilter();
    }

    @Bean
    FilterRegistrationBean<AuditLoggingFilter> auditLoggingFilterRegistration(
            AuditLoggingFilter filter
    ) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setName("yghAuditLoggingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setEnabled(true);
        registration.addUrlPatterns("/api/*", "/internal/*");
        return registration;
    }
}
