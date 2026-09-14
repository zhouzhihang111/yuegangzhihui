package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ResourceLock("sentinel-gateway-rules")
class GatewayContractIntegrationTest {

    private static final String ISSUER = "https://auth.example.test";
    private static final String AUDIENCE = "ygh-api";
    private static final String KEY_ID = "gateway-contract-key";

    @Test
    void realNettyEnforcesRoutesJwtTrustBoundariesAndRateLimitContract() throws Exception {
        Set<GatewayFlowRule> originalRules = Set.copyOf(GatewayRuleManager.getRules());
        KeyPair keyPair = rsaKeyPair();
        String jwks = jwks(keyPair);
        var received = new CopyOnWriteArrayList<ReceivedRequest>();
        var jwksRequests = new java.util.concurrent.atomic.AtomicInteger();
        var backend = reactor.netty.http.server.HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    String path = java.net.URI.create(request.uri()).getPath();
                    if ("/jwks".equals(path)) {
                        jwksRequests.incrementAndGet();
                        return response.header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .sendString(Mono.just(jwks));
                    }
                    received.add(new ReceivedRequest(
                            path,
                            request.requestHeaders().get(GatewayHeaders.USER_ID),
                            request.requestHeaders().get(GatewayHeaders.ROLES)));
                    return response.status(204).send();
                })
                .bindNow();
        try {
            String backendUri = "http://127.0.0.1:" + backend.port();
            try (var context = new SpringApplicationBuilder(GatewayApplication.class)
                    .web(WebApplicationType.REACTIVE)
                    .run(
                            "--server.port=0",
                            "--spring.cloud.nacos.discovery.enabled=false",
                            "--spring.cloud.nacos.server-addr=127.0.0.1:1",
                            simpleInstance("ygh-auth-service", backendUri),
                            simpleInstance("ygh-user-service", backendUri),
                            simpleInstance("ygh-system-service", backendUri),
                            simpleInstance("ygh-product-service", backendUri),
                            simpleInstance("ygh-inventory-service", backendUri),
                            simpleInstance("ygh-order-service", backendUri),
                            simpleInstance("ygh-wallet-service", backendUri),
                            simpleInstance("ygh-knowledge-service", backendUri),
                            simpleInstance("ygh-ai-service", backendUri),
                            simpleInstance("ygh-training-service", backendUri),
                            simpleInstance("ygh-notification-service", backendUri),
                            simpleInstance("ygh-admin-service", backendUri),
                            "--ygh.gateway.cors.allowed-origins=https://mall.example.test",
                            "--ygh.security.jwt.issuer=" + ISSUER,
                            "--ygh.security.jwt.jwk-set-uri=" + backendUri + "/jwks",
                            "--ygh.security.jwt.audience=" + AUDIENCE,
                            "--ygh.security.session-validation.enabled=false",
                            "--YGH_REDIS_HOST=127.0.0.1",
                            "--YGH_REDIS_PASSWORD=change-me",
                            "--YGH_REDIS_ENVIRONMENT=test",
                            "--YGH_INTERNAL_REQUEST_HMAC_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")) {
                Integer gatewayPort = context.getEnvironment()
                        .getProperty("local.server.port", Integer.class);
                WebTestClient client = WebTestClient.bindToServer()
                        .baseUrl("http://127.0.0.1:" + gatewayPort)
                        .build();
                String customerToken = token(keyPair, "customer-1001", List.of("CUSTOMER"));
                String adminToken = token(keyPair, "admin-1001", List.of("ADMIN"));
                var decoded = context.getBean(org.springframework.security.oauth2.jwt.ReactiveJwtDecoder.class)
                        .decode(customerToken).block();
                assertThat(decoded)
                        .as("JWKS requests=%s backend requests=%s", jwksRequests.get(), received)
                        .isNotNull();
                assertThat(jwksRequests).hasValue(1);

                client.get().uri("/api/v1/users/me")
                        .header(GatewayHeaders.TRACE_ID, "trace-contract-401")
                        .exchange()
                        .expectStatus().isUnauthorized()
                        .expectBody()
                        .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                        .jsonPath("$.traceId").isEqualTo("trace-contract-401");
                client.get().uri("/api/v1/admin/dashboard")
                        .headers(headers -> headers.setBearerAuth(customerToken))
                        .exchange()
                        .expectStatus().isForbidden()
                        .expectBody()
                        .jsonPath("$.code").isEqualTo("PERMISSION_DENIED");

                client.post().uri("/api/v1/auth/login")
                        .exchange()
                        .expectStatus().isNoContent();
                GatewayRuleManager.loadRules(Set.of(new GatewayFlowRule("auth-service")
                        .setCount(0)
                        .setIntervalSec(1)
                        .setBurst(0)));
                client.post().uri("/api/v1/auth/login")
                        .header(GatewayHeaders.TRACE_ID, "trace-contract-429")
                        .exchange()
                        .expectStatus().isEqualTo(429)
                        .expectHeader().valueEquals("Retry-After", "1")
                        .expectBody()
                        .jsonPath("$.code").isEqualTo("RATE_LIMITED")
                        .jsonPath("$.traceId").isEqualTo("trace-contract-429");

                client.get().uri("/api/v1/users/me")
                        .headers(headers -> headers.setBearerAuth(customerToken))
                        .exchange()
                        .expectStatus().isNoContent();
                client.get().uri("/api/v1/system/config")
                        .headers(headers -> headers.setBearerAuth(adminToken))
                        .exchange()
                        .expectStatus().isNoContent();
                client.get().uri("/api/v1/admin/dashboard")
                        .headers(headers -> headers.setBearerAuth(adminToken))
                        .exchange()
                        .expectStatus().isNoContent();

                assertThat(received).extracting(ReceivedRequest::path)
                        .containsExactlyInAnyOrder(
                                "/api/v1/auth/login",
                                "/api/v1/users/me",
                                "/api/v1/system/config",
                                "/api/v1/admin/dashboard");
                assertThat(received).filteredOn(item -> "/api/v1/users/me".equals(item.path()))
                        .singleElement()
                        .satisfies(item -> {
                            assertThat(item.userId()).isEqualTo("customer-1001");
                            assertThat(item.roles()).isEqualTo("CUSTOMER");
                        });
                assertThat(received).filteredOn(item -> item.path().startsWith("/api/v1/admin"))
                        .singleElement()
                        .satisfies(item -> {
                            assertThat(item.userId()).isEqualTo("admin-1001");
                            assertThat(item.roles()).isEqualTo("ADMIN");
                        });
            }
        } finally {
            GatewayRuleManager.loadRules(originalRules);
            backend.disposeNow();
        }
    }

    private static String simpleInstance(String serviceName, String uri) {
        return "--spring.cloud.discovery.client.simple.instances."
                + serviceName + "[0].uri=" + uri;
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String jwks(KeyPair keyPair) {
        var key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID(KEY_ID)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return JSONObjectUtils.toJSONString(java.util.Map.of(
                "keys", List.of(key.toPublicJWK().toJSONObject())));
    }

    private static String token(KeyPair keyPair, String subject, List<String> roles) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .jwtID("contract-" + subject)
                .subject(subject)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now.minusSeconds(5)))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .claim("roles", roles)
                .claim("permissions", List.of("user:profile:read"))
                .build();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    private record ReceivedRequest(String path, String userId, String roles) {
    }
}
