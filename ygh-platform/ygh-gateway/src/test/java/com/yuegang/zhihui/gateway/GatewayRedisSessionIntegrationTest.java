package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.common.redis.*;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

class GatewayRedisSessionIntegrationTest {
    private static final org.testcontainers.containers.GenericContainer<?> REDIS = YghTestContainerFactory.redis();
    private static LettuceConnectionFactory connectionFactory;
    private static RedisSessionStateStore sessions;
    private static ReactiveRedisSessionValidator validator;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        var sync = new StringRedisTemplate(connectionFactory);
        sync.afterPropertiesSet();
        var keys = new SessionRedisKeys(new RedisKeyBuilder(), "test");
        sessions = new RedisSessionStateStore(sync, keys);
        validator = new ReactiveRedisSessionValidator(
                new ReactiveStringRedisTemplate(connectionFactory, RedisSerializationContext.string()), keys);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) connectionFactory.destroy();
        REDIS.stop();
    }

    @Test
    void gatewayStopsRevokedAndDisabledSessionsBeforeDownstreamRouting() {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(60);
        String jti = java.util.UUID.randomUUID().toString();
        sessions.register(7, jti, expiry, now);
        Jwt jwt = Jwt.withTokenValue("signed-token").header("alg", "RS256")
                .subject("42").issuedAt(now.minusSeconds(1)).expiresAt(expiry).claim("jti", jti)
                .claim("account_id", "7").claim("roles", List.of("USER"))
                .claim("permissions", List.of("order:read")).build();
        var authentication = new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());
        var filter = new JwtSessionValidationFilter(
                validator, new GatewaySecurityErrorWriter(new tools.jackson.databind.ObjectMapper()));

        AtomicInteger calls = new AtomicInteger();
        var activeExchange = exchange();
        filter.filter(activeExchange, ignored -> { calls.incrementAndGet(); return Mono.empty(); })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block();
        assertThat(calls).hasValue(1);

        sessions.revoke(7, jti, expiry, now);
        var revokedExchange = exchange();
        filter.filter(revokedExchange, ignored -> { calls.incrementAndGet(); return Mono.empty(); })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block();
        assertThat(calls).hasValue(1);
        assertThat(revokedExchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders").build());
    }
}
