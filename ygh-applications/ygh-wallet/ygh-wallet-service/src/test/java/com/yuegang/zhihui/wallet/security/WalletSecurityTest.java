package com.yuegang.zhihui.wallet.security;

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

class WalletSecurityTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void verifiesInternalAndUserContexts() {
        Instant now = Instant.now();
        var internal = signedInternal(now);
        var verifier = new WalletInternalVerifier(SECRET);
        assertThatCode(() -> verifier.verify(internal)).doesNotThrowAnyException();
        internal.removeHeader("X-YGH-Service-Signature");
        assertThatThrownBy(() -> verifier.verify(internal)).isInstanceOf(BusinessException.class);

        var user = signedUser(now, "42");
        assertThat(new WalletUserResolver(SECRET, Clock.systemUTC()).resolve(user)).isEqualTo(42);
        assertThatThrownBy(() -> new WalletUserResolver(SECRET, Clock.systemUTC()).resolve(signedUser(now, "0")))
                .isInstanceOf(BusinessException.class);
        user.removeHeader("X-YGH-User-Context-Timestamp");
        assertThatThrownBy(() -> new WalletUserResolver(SECRET, Clock.systemUTC()).resolve(user)).isInstanceOf(BusinessException.class);
    }

    private static MockHttpServletRequest signedInternal(Instant time) {
        var request = new MockHttpServletRequest("GET", "/internal/v1/wallet/references/order-1");
        var metadata = new InternalServiceSignature.Metadata("ygh-order-service", "GET", request.getRequestURI(), time);
        var signatures = new InternalServiceSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        request.addHeader("X-YGH-Service", metadata.service());
        request.addHeader("X-YGH-Service-Timestamp", Long.toString(time.toEpochMilli()));
        request.addHeader("X-YGH-Service-Signature", signatures.sign(metadata));
        return request;
    }

    private static MockHttpServletRequest signedUser(Instant time, String userId) {
        var request = new MockHttpServletRequest("POST", "/api/v1/wallet/recharge");
        var metadata = new InternalUserContextSignature.Metadata(userId, List.of("USER"), List.of(), "trace", "request",
                "POST", request.getRequestURI(), time);
        var signatures = new InternalUserContextSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        request.addHeader("X-YGH-User-Id", userId);
        request.addHeader("X-YGH-Roles", "USER");
        request.addHeader("X-Trace-Id", "trace");
        request.addHeader("X-Request-Id", "request");
        request.addHeader("X-YGH-User-Context-Timestamp", Long.toString(time.toEpochMilli()));
        request.addHeader("X-YGH-User-Context-Signature", signatures.sign(metadata));
        return request;
    }
}
