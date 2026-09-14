package com.yuegang.zhihui.user.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class UserInternalServiceVerifierTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Test
    void acceptsValidSignatureAndRejectsMissingMalformedOrTamperedMetadata() {
        var verifier = new UserInternalServiceVerifier(SECRET);
        Instant now = Instant.now();
        var request = signed(now, "GET", "/internal/v1/users/1");
        assertThatCode(() -> verifier.verify(request)).doesNotThrowAnyException();

        var missing = new MockHttpServletRequest("GET", "/internal/v1/users/1");
        assertThatThrownBy(() -> verifier.verify(missing)).isInstanceOf(BusinessException.class);
        request.removeHeader("X-YGH-Service-Timestamp");
        request.addHeader("X-YGH-Service-Timestamp", "invalid");
        assertThatThrownBy(() -> verifier.verify(request)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> verifier.verify(signed(now.minusSeconds(60), "GET", "/internal/v1/users/1")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> verifier.verify(signed(now, "POST", "/internal/v1/users/1")))
                .isInstanceOf(BusinessException.class);
    }

    private static MockHttpServletRequest signed(Instant time, String signedMethod, String path) {
        var request = new MockHttpServletRequest("GET", path);
        var signatures = new InternalServiceSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        var metadata = new InternalServiceSignature.Metadata("ygh-auth-service", signedMethod, path, time);
        request.addHeader("X-YGH-Service", metadata.service());
        request.addHeader("X-YGH-Service-Timestamp", Long.toString(time.toEpochMilli()));
        request.addHeader("X-YGH-Service-Signature", signatures.sign(metadata));
        return request;
    }
}
