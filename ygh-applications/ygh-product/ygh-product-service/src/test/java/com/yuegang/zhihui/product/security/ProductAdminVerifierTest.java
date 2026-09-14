package com.yuegang.zhihui.product.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ProductAdminVerifierTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void requiresValidSignedAdminContext() {
        Instant now = Instant.now();
        var request = signed(now, List.of("ADMIN"));
        var verifier = new ProductAdminVerifier(SECRET);
        assertThatCode(() -> verifier.verify(request)).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verify(signed(now, List.of("USER")))).isInstanceOf(BusinessException.class);
        request.removeHeader("X-YGH-User-Context-Signature");
        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> verifier.verify(signed(now.minusSeconds(60), List.of("ADMIN"))))
                .isInstanceOf(BusinessException.class);
    }

    private static MockHttpServletRequest signed(Instant time, List<String> roles) {
        var request = new MockHttpServletRequest("POST", "/api/v1/admin/products");
        var metadata = new InternalUserContextSignature.Metadata("42", roles, List.of("product:write"), "trace", "request",
                "POST", request.getRequestURI(), time);
        var signatures = new InternalUserContextSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        request.addHeader("X-YGH-User-Id", "42");
        request.addHeader("X-YGH-Roles", String.join(",", roles));
        request.addHeader("X-YGH-Permissions", "product:write");
        request.addHeader("X-Trace-Id", "trace");
        request.addHeader("X-Request-Id", "request");
        request.addHeader("X-YGH-User-Context-Timestamp", Long.toString(time.toEpochMilli()));
        request.addHeader("X-YGH-User-Context-Signature", signatures.sign(metadata));
        return request;
    }
}
