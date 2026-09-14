package com.yuegang.zhihui.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class RequestLoggingAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RequestLoggingAutoConfiguration.class));

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void servletAutoConfigurationProvidesDefaultSinkAndRequestFilter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RequestLogSink.class);
            assertThat(context).hasSingleBean(RequestLoggingFilter.class);
            assertThat(context).hasSingleBean(AuditLoggingFilter.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);

            var filter = context.getBean(RequestLoggingFilter.class);
            var registrations = context.getBeansOfType(FilterRegistrationBean.class);
            assertThat(registrations).hasSize(2);
            var registration = registrations.get("requestLoggingFilterRegistration");
            assertThat(registration.getFilter()).isSameAs(filter);
            assertThat(registration.isEnabled()).isTrue();
            assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");

            var auditRegistration = registrations.get("auditLoggingFilterRegistration");
            assertThat(auditRegistration.getFilter())
                    .isSameAs(context.getBean(AuditLoggingFilter.class));
            assertThat(auditRegistration.isEnabled()).isTrue();
            assertThat(auditRegistration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 20);
            assertThat(auditRegistration.getUrlPatterns()).containsExactly("/api/*", "/internal/*");
        });
    }

    @Test
    void autoConfigurationIsRegisteredForBootDiscovery() throws IOException {
        var resourceName = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (var input = RequestLoggingAutoConfigurationTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            assertThat(input).as(resourceName).isNotNull();
            var registrations = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(registrations)
                    .contains("com.yuegang.zhihui.common.web.RequestLoggingAutoConfiguration");
        }
    }
}
