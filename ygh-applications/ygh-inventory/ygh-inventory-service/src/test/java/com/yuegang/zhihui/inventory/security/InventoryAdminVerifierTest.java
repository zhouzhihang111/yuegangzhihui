package com.yuegang.zhihui.inventory.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class InventoryAdminVerifierTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private final InventoryAdminVerifier verifier = new InventoryAdminVerifier(Base64.getEncoder().encodeToString(SECRET));

    @Test
    void acceptsAdminOrReadPermissionAndRejectsUntrustedContexts() {
        assertThatCode(() -> verifier.require(signed(List.of("ADMIN"), List.of())))
                .doesNotThrowAnyException();
        assertThatCode(() -> verifier.require(signed(List.of("EMPLOYEE"), List.of("inventory:read"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.require(signed(List.of("USER"), List.of())))
                .isInstanceOf(BusinessException.class);

        var forged = signed(List.of("ADMIN"), List.of());
        forged.removeHeader("X-YGH-User-Context-Signature");
        forged.addHeader("X-YGH-User-Context-Signature", "forged");
        assertThatThrownBy(() -> verifier.require(forged)).isInstanceOf(BusinessException.class);

        var missing = signed(List.of("ADMIN"), List.of());
        missing.removeHeader("X-YGH-User-Id");
        assertThatThrownBy(() -> verifier.require(missing)).isInstanceOf(BusinessException.class);

        var invalidTimestamp = signed(List.of("ADMIN"), List.of());
        invalidTimestamp.removeHeader("X-YGH-User-Context-Timestamp");
        invalidTimestamp.addHeader("X-YGH-User-Context-Timestamp", "invalid");
        assertThatThrownBy(() -> verifier.require(invalidTimestamp)).isInstanceOf(BusinessException.class);
    }

    private static MockHttpServletRequest signed(List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/inventory");
        var metadata = new InternalUserContextSignature.Metadata(
                "42", roles, permissions, "trace-1234", "request-1234", "GET", request.getRequestURI(), now);
        var signatures = new InternalUserContextSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        request.addHeader("X-YGH-User-Id", "42");
        request.addHeader("X-YGH-Roles", String.join(",", roles));
        request.addHeader("X-YGH-Permissions", String.join(",", permissions));
        request.addHeader("X-Trace-Id", "trace-1234");
        request.addHeader("X-Request-Id", "request-1234");
        request.addHeader("X-YGH-User-Context-Timestamp", Long.toString(now.toEpochMilli()));
        request.addHeader("X-YGH-User-Context-Signature", signatures.sign(metadata));
        return request;
    }
}
