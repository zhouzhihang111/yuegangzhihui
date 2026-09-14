package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditContractTest {

    @Test
    void shouldExposeStablePermissionDeniedBusinessCode() {
        PermissionDeniedException exception = new PermissionDeniedException();

        assertThat(exception.errorCode().code()).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void shouldPublishImmutableSecurityDecisionWithoutSensitiveCredentials() {
        AtomicReference<SecurityAuditEvent> captured = new AtomicReference<>();
        SecurityAuditPublisher publisher = captured::set;
        SecurityAuditEvent event = new SecurityAuditEvent(
                Instant.parse("2026-07-11T08:00:00Z"),
                "user-1",
                "READ_ADDRESS",
                "user:address:read",
                SecurityDecision.DENIED,
                "RESOURCE_NOT_OWNED",
                "trace-1"
        );

        publisher.publish(event);

        assertThat(captured.get()).isEqualTo(event);
        assertThat(event.toString()).doesNotContain("token", "password", "secret");
    }
}
