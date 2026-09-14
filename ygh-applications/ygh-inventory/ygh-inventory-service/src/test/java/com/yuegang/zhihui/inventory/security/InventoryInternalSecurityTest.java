package com.yuegang.zhihui.inventory.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class InventoryInternalSecurityTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsAllowedSignedServiceOnly() {
        Instant now = Instant.now();
        var verifier = new InventoryInternalSecurity(SECRET);
        assertThatCode(() -> verifier.verify(signed("ygh-order-service", now))).doesNotThrowAnyException();
        assertThatCode(() -> verifier.verify(signed("ygh-admin-service", now))).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verify(signed("unknown", now))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> verifier.verify(signed("ygh-order-service", now.minusSeconds(60)))).isInstanceOf(BusinessException.class);
        var missing = signed("ygh-order-service", now);
        missing.removeHeader("X-YGH-Service-Signature");
        assertThatThrownBy(() -> verifier.verify(missing)).isInstanceOf(BusinessException.class);
    }

    private static MockHttpServletRequest signed(String service, Instant time) {
        var request = new MockHttpServletRequest("POST", "/internal/v1/inventory/reserve");
        var metadata = new InternalServiceSignature.Metadata(service, "POST", request.getRequestURI(), time);
        var signatures = new InternalServiceSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        request.addHeader("X-YGH-Service", service);
        request.addHeader("X-YGH-Service-Timestamp", Long.toString(time.toEpochMilli()));
        request.addHeader("X-YGH-Service-Signature", signatures.sign(metadata));
        return request;
    }
}
