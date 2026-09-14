package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.common.security.InternalRequestSignature;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class TrustedClientIpFilterTest {
    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void replacesSpoofedHeadersWithSignedRemoteAddress() {
        var request = MockServerHttpRequest.post("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress("192.0.2.8", 54321))
                .header(GatewayHeaders.TRACE_ID, "trace-1")
                .header(GatewayHeaders.REQUEST_ID, "request-1")
                .header(GatewayHeaders.CLIENT_IP, "203.0.113.9")
                .header(GatewayHeaders.CLIENT_IP_SIGNATURE, "attacker")
                .build();
        var captured = new AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest>();
        var filter = new TrustedClientIpFilter(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

        filter.filter(MockServerWebExchange.from(request), exchange -> {
            captured.set(exchange.getRequest());
            return reactor.core.publisher.Mono.empty();
        }).block(Duration.ofSeconds(2));

        var forwarded = captured.get();
        assertThat(forwarded.getHeaders().getFirst(GatewayHeaders.CLIENT_IP)).isEqualTo("192.0.2.8");
        assertThat(forwarded.getHeaders().getFirst(GatewayHeaders.CLIENT_IP_TIMESTAMP))
                .isEqualTo(Long.toString(NOW.toEpochMilli()));
        var verifier = new InternalRequestSignature(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        var metadata = new InternalRequestSignature.Metadata(
                "192.0.2.8", "trace-1", "request-1", "POST", "/api/v1/auth/login", NOW);
        assertThat(verifier.verify(metadata,
                forwarded.getHeaders().getFirst(GatewayHeaders.CLIENT_IP_SIGNATURE))).isTrue();
        assertThat(filter.getOrder()).isLessThan(new TrustedUserContextFilter().getOrder());
    }
}
