package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalRequestSignature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class TrustedClientContextResolverTest {
    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private final InternalRequestSignature signatures = new InternalRequestSignature(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII),
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
    private final TrustedClientContextResolver resolver = new TrustedClientContextResolver(signatures);

    @Test
    void acceptsOnlyMetadataSignedByGateway() {
        MockHttpServletRequest request = request();
        var metadata = new InternalRequestSignature.Metadata(
                "192.0.2.8", "trace-1", "request-1", "POST", "/api/v1/auth/login", NOW);
        request.addHeader(TrustedClientContextResolver.SIGNATURE, signatures.sign(metadata));

        LoginSecurityContext context = resolver.resolve(request);

        assertThat(context.clientIp().getHostAddress()).isEqualTo("192.0.2.8");
        assertThat(context.traceId()).isEqualTo("trace-1");
        request.removeHeader(TrustedClientContextResolver.CLIENT_IP);
        request.addHeader(TrustedClientContextResolver.CLIENT_IP, "192.0.2.9");
        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMissingSignatureAndHostNames() {
        assertThatThrownBy(() -> resolver.resolve(request())).isInstanceOf(BusinessException.class);
        MockHttpServletRequest host = request();
        host.removeHeader(TrustedClientContextResolver.CLIENT_IP);
        host.addHeader(TrustedClientContextResolver.CLIENT_IP, "dead");
        host.addHeader(TrustedClientContextResolver.SIGNATURE, "0".repeat(64));
        assertThatThrownBy(() -> resolver.resolve(host)).isInstanceOf(BusinessException.class);
    }

    private static MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setAttribute("traceId", "trace-1");
        request.addHeader("X-Trace-Id", "trace-1");
        request.addHeader("X-Request-Id", "request-1");
        request.addHeader(TrustedClientContextResolver.CLIENT_IP, "192.0.2.8");
        request.addHeader(TrustedClientContextResolver.TIMESTAMP, Long.toString(NOW.toEpochMilli()));
        return request;
    }
}
