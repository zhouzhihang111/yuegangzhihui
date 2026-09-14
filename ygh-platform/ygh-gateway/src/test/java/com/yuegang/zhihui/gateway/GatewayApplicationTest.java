package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.reactive.ConfigurableReactiveWebApplicationContext;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ClassUtils;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ResourceLock("sentinel-gateway-rules")
class GatewayApplicationTest {

    @Test
    void gatewayBootstrapAndConfigurationFollowServiceContract() throws IOException {
        assertThat(GatewayApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();

        var configuration = new ClassPathResource("application.yml");
        assertThat(configuration.exists()).isTrue();
        assertThat(configuration.getContentAsString(StandardCharsets.UTF_8))
                .contains("name: ygh-gateway")
                .contains("web-application-type: reactive")
                .contains("server-addr: ${YGH_NACOS_SERVER_ADDR}")
                .contains("password: ${YGH_NACOS_PASSWORD}")
                .contains("allowed-origins: ${YGH_GATEWAY_CORS_ALLOWED_ORIGINS}")
                .contains("shutdown: graceful")
                .contains("add-additional-paths: true")
                .contains("timeout-per-shutdown-phase: 20s")
                .doesNotContain("YGH_GATEWAY_CORS_ALLOWED_ORIGINS:");
    }

    @Test
    void gatewayClasspathIsReactiveAndDoesNotContainSpringMvc() {
        ClassLoader classLoader = GatewayApplicationTest.class.getClassLoader();

        assertThat(ClassUtils.isPresent(
                "org.springframework.web.reactive.DispatcherHandler", classLoader)).isTrue();
        assertThat(ClassUtils.isPresent(
                "org.springframework.web.servlet.DispatcherServlet", classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(
                "jakarta.servlet.Servlet", classLoader)).isFalse();
        assertThat(ClassUtils.isPresent(
                "com.alibaba.cloud.nacos.registry.NacosServiceRegistry", classLoader)).isTrue();
        assertThat(ClassUtils.isPresent(
                "org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter", classLoader)).isTrue();
        assertThat(ClassUtils.isPresent(
                "com.github.benmanes.caffeine.cache.Caffeine", classLoader)).isTrue();
        assertThat(ClassUtils.isPresent(
                "com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter", classLoader)).isTrue();
    }

    @Test
    void gatewayStartsAsReactiveApplicationWithoutExternalInfrastructure() {
        var backendCalls = new java.util.concurrent.atomic.AtomicInteger();
        var backend = reactor.netty.http.server.HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> Mono.fromRunnable(backendCalls::incrementAndGet)
                        .then(request.receive().then())
                        .then(response.status(204).send()))
                .bindNow();
        try {
            try (var context = new SpringApplicationBuilder(GatewayApplication.class)
                .web(WebApplicationType.REACTIVE)
                .run(
                        "--server.port=0",
                        "--spring.cloud.nacos.discovery.enabled=false",
                        "--spring.cloud.nacos.server-addr=127.0.0.1:1",
                        "--spring.cloud.discovery.client.simple.instances.ygh-auth-service[0].uri="
                                + "http://127.0.0.1:" + backend.port(),
                        "--ygh.gateway.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173",
                        "--ygh.security.jwt.issuer=https://auth.example.test",
                        "--ygh.security.jwt.jwk-set-uri=https://auth.example.test/.well-known/jwks.json",
                        "--ygh.security.jwt.audience=ygh-api",
                        "--ygh.security.session-validation.enabled=false",
                        "--management.health.redis.enabled=false",
                        "--YGH_REDIS_HOST=127.0.0.1",
                        "--YGH_REDIS_PASSWORD=change-me",
                        "--YGH_REDIS_ENVIRONMENT=test",
                        "--ygh.internal-request.enabled=false",
                        "--YGH_INTERNAL_REQUEST_HMAC_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")) {
            assertThat(context).isInstanceOf(ConfigurableReactiveWebApplicationContext.class);
            assertThat(context.containsBean("webHandler")).isTrue();

            var definitions = context.getBean(RouteDefinitionLocator.class)
                    .getRouteDefinitions().collectList().block();
            assertThat(definitions).isNotNull();
            Map<String, RouteDefinition> byId = definitions.stream()
                    .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));
            assertThat(byId).containsOnlyKeys(
                    "auth-service", "user-service", "system-service", "product-service",
                    "inventory-service", "order-service", "wallet-service", "knowledge-service",
                    "ai-service", "training-service", "notification-service", "admin-service");
            assertThat(byId.get("auth-service").getUri()).isEqualTo(URI.create("lb://ygh-auth-service"));
            assertThat(byId.get("user-service").getUri()).isEqualTo(URI.create("lb://ygh-user-service"));
            assertThat(byId.get("system-service").getUri()).isEqualTo(URI.create("lb://ygh-system-service"));
            assertThat(byId.get("admin-service").getUri()).isEqualTo(URI.create("lb://ygh-admin-service"));
            assertThat(byId.get("product-service").getUri()).isEqualTo(URI.create("lb://ygh-product-service"));
            assertThat(byId.get("inventory-service").getUri()).isEqualTo(URI.create("lb://ygh-inventory-service"));
            assertThat(byId.get("order-service").getUri()).isEqualTo(URI.create("lb://ygh-order-service"));
            assertThat(byId.get("wallet-service").getUri()).isEqualTo(URI.create("lb://ygh-wallet-service"));
            assertThat(byId.get("knowledge-service").getUri()).isEqualTo(URI.create("lb://ygh-knowledge-service"));
            assertThat(byId.get("ai-service").getUri()).isEqualTo(URI.create("lb://ygh-ai-service"));
            assertThat(byId.get("training-service").getUri()).isEqualTo(URI.create("lb://ygh-training-service"));
            assertThat(byId.get("notification-service").getUri()).isEqualTo(URI.create("lb://ygh-notification-service"));
            assertRoutePaths(byId.get("auth-service"), Set.of("/api/v1/auth/**"));
            assertRoutePaths(byId.get("user-service"), Set.of(
                    "/api/v1/users/**",
                    "/api/v1/organization/**"));
            assertRoutePaths(byId.get("system-service"), Set.of(
                    "/api/v1/system/**",
                    "/api/v1/roles/**",
                    "/api/v1/permissions/**"));
            assertRoutePaths(byId.get("product-service"), Set.of("/api/v1/products/**", "/api/v1/product-categories", "/api/v1/product-brands", "/api/v1/admin/products/**", "/api/v1/admin/product-categories", "/api/v1/admin/product-brands"));
            assertRoutePaths(byId.get("inventory-service"), Set.of("/api/v1/admin/inventory/**"));
            assertRoutePaths(byId.get("order-service"), Set.of("/api/v1/cart/**", "/api/v1/orders/**", "/api/v1/admin/orders/**"));
            assertRoutePaths(byId.get("wallet-service"), Set.of("/api/v1/wallet/**", "/api/v1/admin/wallet/**"));
            assertRoutePaths(byId.get("knowledge-service"), Set.of("/api/v1/knowledge/**", "/api/v1/admin/knowledge/**"));
            assertRoutePaths(byId.get("ai-service"), Set.of("/api/v1/ai/**", "/api/v1/admin/ai/**"));
            assertRoutePaths(byId.get("training-service"), Set.of("/api/v1/training/**"));
            assertRoutePaths(byId.get("notification-service"), Set.of("/api/v1/notifications/**", "/api/v1/admin/notifications/**"));
            assertRoutePaths(byId.get("admin-service"), Set.of("/api/v1/admin/dashboard", "/api/v1/admin/audit-logs"));

            var corsExchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                    org.springframework.mock.http.server.reactive.MockServerHttpRequest
                            .options("/api/v1/auth/login").build());
            assertThat(context.getBean(CorsConfigurationSource.class)
                    .getCorsConfiguration(corsExchange).getAllowedOrigins())
                    .contains("http://localhost:5173");
            assertThat(context.getBean(GatewayCorsWebFilter.class).getOrder())
                    .isLessThan(context.getBean(GatewayRequestGuardFilter.class).getOrder());

            WebTestClient client = WebTestClient.bindToApplicationContext(context)
                    .apply(springSecurity())
                    .build();
            Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
            assertThat(port).isNotNull().isPositive();
            WebTestClient serverClient = WebTestClient.bindToServer()
                    .baseUrl("http://127.0.0.1:" + port)
                    .build();
            serverClient.get().uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                    .expectHeader().valueEquals("X-Frame-Options", "DENY")
                    .expectHeader().doesNotExist("Server");
            for (String probe : Set.of(
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/livez",
                    "/readyz")) {
                serverClient.get().uri(probe)
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo("UP")
                        .jsonPath("$.components").doesNotExist();
            }
            serverClient.get().uri("/actuator/env")
                    .exchange()
                    .expectStatus().is4xxClientError();
            serverClient.get().uri("/actuator/health/nacosDiscovery")
                    .exchange()
                    .expectStatus().isUnauthorized();
            serverClient.options().uri("/api/v1/auth/login")
                    .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            serverClient.options().uri("/api/v1/auth/login")
                    .header(HttpHeaders.ORIGIN, "https://attacker.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
            serverClient.post().uri("/api/v1/auth/login")
                    .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                    .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(new byte[2 * 1024 * 1024 + 1])
                    .exchange()
                    .expectStatus().isEqualTo(413)
                    .expectHeader().valueEquals(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
            var clientBuffers = org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance;
            var httpClientLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                    .getLogger("reactor.netty.http.client.HttpClientConnect");
            var clientLogEvents = new ch.qos.logback.core.read.ListAppender<
                    ch.qos.logback.classic.spi.ILoggingEvent>();
            clientLogEvents.start();
            httpClientLogger.addAppender(clientLogEvents);
            try {
                serverClient.post().uri("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromDataBuffers(Flux.just(
                                clientBuffers.wrap(new byte[2 * 1024 * 1024 + 1]))))
                        .exchange()
                        .expectStatus().isEqualTo(413)
                        .expectHeader().valueEquals(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                        .expectBody()
                        .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
            } finally {
                httpClientLogger.detachAppender(clientLogEvents);
                clientLogEvents.stop();
            }
            assertThat(backendCalls).hasValue(0);
            assertThat(clientLogEvents.list)
                    .noneMatch(event -> event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN));
            serverClient.post().uri("/api/v1/auth/login")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromDataBuffers(Flux.just(
                            clientBuffers.wrap("{}".getBytes(StandardCharsets.UTF_8)))))
                    .exchange()
                    .expectStatus().isNoContent();
            assertThat(backendCalls).hasValue(1);
            serverClient.post().uri("/api/v1/orders/import")
                    .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                    .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=test")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectHeader().valueEquals(
                            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("PERMISSION_DENIED");
            for (String unsafeUploadPath : Set.of(
                    "/api/v1/knowledge/documents/",
                    "/api/v1/knowledge/documents;version=1",
                    "/api/v1/knowledge/documents/extra")) {
                serverClient.post().uri(unsafeUploadPath)
                        .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=test")
                        .exchange()
                        .expectStatus().isForbidden();
            }
            serverClient.put().uri("/api/v1/knowledge/documents")
                    .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=test")
                    .exchange()
                    .expectStatus().isForbidden();
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-1001")))
                    .get().uri("/api/v1/users/me")
                    .header(GatewayHeaders.TRACE_ID, "trace-dependency-1234")
                    .exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().valueEquals(GatewayHeaders.TRACE_ID, "trace-dependency-1234")
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("DEPENDENCY_UNAVAILABLE")
                    .jsonPath("$.traceId").isEqualTo("trace-dependency-1234");
            client.get().uri("/api/v1/users/me")
                    .header(GatewayHeaders.TRACE_ID, "trace-security-1234")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals(GatewayHeaders.TRACE_ID, "trace-security-1234")
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                    .jsonPath("$.traceId").isEqualTo("trace-security-1234");
            client.post().uri("/api/v1/auth/logout")
                    .exchange()
                    .expectStatus().isUnauthorized();
            client.post().uri("/api/v1/auth/captcha")
                    .exchange()
                    .expectStatus().isUnauthorized();
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-1001")))
                    .get().uri("/api/v1/users/me")
                    .exchange()
                    .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-1001")))
                    .get().uri("/api/v1/organization/departments")
                    .exchange()
                    .expectStatus().isForbidden();
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("employee-1001"))
                            .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                    .get().uri("/api/v1/organization/departments")
                    .exchange()
                    .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("employee-1001"))
                            .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                    .get().uri("/api/v1/admin/dashboard")
                    .exchange()
                    .expectStatus().isForbidden();
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-1001"))
                            .authorities(new SimpleGrantedAuthority("PERM_ROLE_ADMIN")))
                    .get().uri("/api/v1/admin/dashboard")
                    .exchange()
                    .expectStatus().isForbidden();
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("admin-1001"))
                            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .get().uri("/api/v1/admin/dashboard")
                    .exchange()
                    .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
            client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-1001")))
                    .get().uri("/internal/operations")
                    .exchange()
                    .expectStatus().isForbidden()
                    .expectBody()
                    .jsonPath("$.code").isEqualTo("PERMISSION_DENIED");
            }
        } finally {
            backend.disposeNow();
        }
    }

    private static void assertRoutePaths(RouteDefinition route, Set<String> expectedPaths) {
        assertThat(route.getPredicates()).singleElement().satisfies(predicate -> {
            assertThat(predicate.getName()).isEqualTo("Path");
            Set<String> actualPaths = predicate.getArgs().values().stream()
                    .flatMap(value -> Arrays.stream(value.split(",")))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            assertThat(actualPaths).containsExactlyInAnyOrderElementsOf(expectedPaths);
            assertThat(actualPaths).doesNotContain("/api/v1/**");
        });
    }
}
