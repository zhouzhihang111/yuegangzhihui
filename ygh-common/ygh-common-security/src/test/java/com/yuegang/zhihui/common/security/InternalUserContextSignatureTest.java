package com.yuegang.zhihui.common.security;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalUserContextSignatureTest {
    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    @Test void bindsIdentityAuthoritiesAndRequestMetadata() {
        var signatures = new InternalUserContextSignature(new byte[32], Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        var metadata = new InternalUserContextSignature.Metadata("42", List.of("CUSTOMER"), List.of("user:read"),
                "trace-123456", "request-123456", "GET", "/api/v1/users/me", NOW);
        String signed = signatures.sign(metadata);
        assertThat(signatures.verify(metadata, signed)).isTrue();
        var tampered = new InternalUserContextSignature.Metadata("43", List.of("ADMIN"), List.of("user:write"),
                "trace-123456", "request-123456", "GET", "/api/v1/users/me", NOW);
        assertThat(signatures.verify(tampered, signed)).isFalse();
    }
    @Test void rejectsExpiredOrUnsafeMetadata() {
        var signatures = new InternalUserContextSignature(new byte[32], Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        var expired = new InternalUserContextSignature.Metadata("42", List.of(), List.of(), "trace-123456",
                "request-123456", "GET", "/api/v1/users/me", NOW.minusSeconds(31));
        assertThat(signatures.verify(expired, "0".repeat(64))).isFalse();
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("42", List.of("bad role"), List.of(),
                "trace-123456", "request-123456", "GET", "/api/v1/users/me", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(signatures.verify(expired, null)).isFalse();
        assertThat(signatures.verify(expired, "not-a-signature")).isFalse();
    }
    @Test void validatesConstructorBoundsAndCanonicalizesAuthorities() {
        assertThatThrownBy(() -> new InternalUserContextSignature(new byte[31], Clock.systemUTC(), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalUserContextSignature(new byte[32], Clock.systemUTC(), Duration.ofMillis(999)))
                .isInstanceOf(IllegalArgumentException.class);
        var signatures = new InternalUserContextSignature(new byte[32], Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));
        var metadata = new InternalUserContextSignature.Metadata("42", List.of("USER", "ADMIN", "USER"),
                List.of("user:write", "user:read"), "trace-123456", "request-123456",
                "PUT", "/api/v1/users/me", NOW);
        assertThat(metadata.roles()).containsExactly("ADMIN", "USER");
        assertThat(signatures.verify(metadata, signatures.sign(metadata))).isTrue();
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("bad user", List.of(), List.of(),
                "trace-123456", "request-123456", "GET", "/", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("42", List.of(), List.of(),
                "bad trace", "request-123456", "GET", "/", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("42", List.of(), List.of(),
                "trace-123456", "request-123456", "get", "/", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("42", List.of(), List.of(),
                "trace-123456", "request-123456", "GET", "relative", NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
