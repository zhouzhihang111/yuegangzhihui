package com.yuegang.zhihui.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SystemSecurityVerifierTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void verifiesServiceAndTrustedUserMetadata() {
        Instant now = Instant.now();
        var serviceRequest = new MockHttpServletRequest("GET", "/internal/v1/authorizations/42");
        var serviceMetadata = new InternalServiceSignature.Metadata("ygh-auth-service", "GET", serviceRequest.getRequestURI(), now);
        var serviceSignatures = new InternalServiceSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        serviceRequest.addHeader("X-YGH-Service", serviceMetadata.service());
        serviceRequest.addHeader("X-YGH-Service-Timestamp", Long.toString(now.toEpochMilli()));
        serviceRequest.addHeader("X-YGH-Service-Signature", serviceSignatures.sign(serviceMetadata));
        var verifier = new InternalServiceVerifier(SECRET, Clock.systemUTC());
        assertThatCode(() -> verifier.verify(serviceRequest, "ygh-auth-service")).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verify(serviceRequest, "other-service")).isInstanceOf(BusinessException.class);
        serviceRequest.removeHeader("X-YGH-Service-Signature");
        assertThatThrownBy(() -> verifier.verify(serviceRequest, "ygh-auth-service")).isInstanceOf(BusinessException.class);

        var userRequest = new MockHttpServletRequest("POST", "/api/v1/system/flags/ai.customer");
        var userMetadata = new InternalUserContextSignature.Metadata("42", List.of("ADMIN"), List.of("system:write"),
                "trace-1", "request-1", "POST", userRequest.getRequestURI(), now);
        var userSignatures = new InternalUserContextSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        userRequest.addHeader("X-YGH-User-Id", "42");
        userRequest.addHeader("X-YGH-Roles", "ADMIN");
        userRequest.addHeader("X-YGH-Permissions", "system:write");
        userRequest.addHeader("X-Trace-Id", "trace-1");
        userRequest.addHeader("X-Request-Id", "request-1");
        userRequest.addHeader("X-YGH-User-Context-Timestamp", Long.toString(now.toEpochMilli()));
        userRequest.addHeader("X-YGH-User-Context-Signature", userSignatures.sign(userMetadata));
        var resolver = new SystemTrustedUserContextResolver(SECRET, Clock.systemUTC());
        assertThat(resolver.resolve(userRequest).permissions()).containsExactly("system:write");
        userRequest.removeHeader("X-YGH-User-Id");
        assertThatThrownBy(() -> resolver.resolve(userRequest)).isInstanceOf(BusinessException.class);
    }
}
