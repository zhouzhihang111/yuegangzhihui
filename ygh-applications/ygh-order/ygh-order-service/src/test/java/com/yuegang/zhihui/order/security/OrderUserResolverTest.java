package com.yuegang.zhihui.order.security;

import static org.assertj.core.api.Assertions.assertThat;
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

class OrderUserResolverTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void resolvesSignedUserAndEnforcesPermission() {
        var resolver = new OrderUserResolver(SECRET);
        var user = signed(List.of("USER"), List.of("order:self:read"));
        assertThat(resolver.resolve(user)).isEqualTo(42);
        assertThat(resolver.requirePermission(user, "order:self:read")).isEqualTo(42);
        assertThatThrownBy(() -> resolver.requirePermission(user, "order:admin:read")).isInstanceOf(BusinessException.class);
        assertThat(resolver.requirePermission(signed(List.of("ADMIN"), List.of()), "anything")).isEqualTo(42);
        user.removeHeader("X-YGH-User-Context-Signature");
        assertThatThrownBy(() -> resolver.resolve(user)).isInstanceOf(BusinessException.class);
    }

    private static MockHttpServletRequest signed(List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        var request = new MockHttpServletRequest("GET", "/api/v1/orders");
        var metadata = new InternalUserContextSignature.Metadata("42", roles, permissions, "trace", "request", "GET",
                request.getRequestURI(), now);
        var signatures = new InternalUserContextSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30));
        request.addHeader("X-YGH-User-Id", "42");
        request.addHeader("X-YGH-Roles", String.join(",", roles));
        request.addHeader("X-YGH-Permissions", String.join(",", permissions));
        request.addHeader("X-Trace-Id", "trace");
        request.addHeader("X-Request-Id", "request");
        request.addHeader("X-YGH-User-Context-Timestamp", Long.toString(now.toEpochMilli()));
        request.addHeader("X-YGH-User-Context-Signature", signatures.sign(metadata));
        return request;
    }
}
