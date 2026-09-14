package com.yuegang.zhihui.gateway;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/** Exact-origin CORS policy for browser clients. */
@Configuration(proxyBeanMethods = false)
class GatewayCorsConfiguration {

    @Bean
    CorsConfigurationSource gatewayCorsConfigurationSource(
            @Value("${ygh.gateway.cors.allowed-origins}") String allowedOrigins) {
        return createSource(allowedOrigins);
    }

    @Bean
    GatewayCorsWebFilter gatewayCorsWebFilter(CorsConfigurationSource configurationSource) {
        return new GatewayCorsWebFilter(configurationSource);
    }

    static UrlBasedCorsConfigurationSource createSource(String configuredOrigins) {
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(GatewayCorsConfiguration::validateOrigin)
                .forEach(origins::add);
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("At least one CORS origin is required");
        }

        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.copyOf(origins));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id",
                "X-Trace-Id", "Accept-Language"));
        cors.setExposedHeaders(List.of("X-Request-Id", "X-Trace-Id", "Retry-After"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static String validateOrigin(String origin) {
        if ("*".equals(origin)) {
            throw new IllegalArgumentException("Wildcard CORS origins are forbidden");
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid CORS origin", exception);
        }
        boolean http = "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
        if (!http || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getPort() == 0 || uri.getPort() > 65_535
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("CORS origin must be an HTTP(S) origin without a path");
        }
        return origin;
    }
}
