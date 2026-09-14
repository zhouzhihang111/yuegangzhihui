package com.yuegang.zhihui.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.Test;

class InternalServiceSignatureTest {
    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private final InternalServiceSignature signatures = new InternalServiceSignature(
            SECRET, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));

    @Test
    void acceptsOnlyUntamperedFreshLowerHexSignatures() {
        var metadata = new InternalServiceSignature.Metadata(
                "ygh-order-service", "POST", "/internal/v1/inventory/reserve", NOW);
        String signature = signatures.sign(metadata);

        assertThat(signature).matches("[0-9a-f]{64}");
        assertThat(signatures.verify(metadata, signature)).isTrue();
        assertThat(signatures.verify(metadata, null)).isFalse();
        assertThat(signatures.verify(metadata, "invalid")).isFalse();
        assertThat(signatures.verify(new InternalServiceSignature.Metadata(
                metadata.service(), "GET", metadata.path(), NOW), signature)).isFalse();
        var stale = new InternalServiceSignature.Metadata(
                metadata.service(), metadata.method(), metadata.path(), NOW.minusSeconds(31));
        assertThat(signatures.verify(stale, signatures.sign(stale))).isFalse();
    }

    @Test
    void rejectsWeakSecretsAndNullInfrastructure() {
        assertThatThrownBy(() -> new InternalServiceSignature(new byte[31], Clock.systemUTC(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalServiceSignature(null, Clock.systemUTC(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalServiceSignature(SECRET, null, Duration.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InternalServiceSignature(SECRET, Clock.systemUTC(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void metadataRejectsUnsafeCanonicalFields() {
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata(null, "GET", "/x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("X", "GET", "/x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", null, "/x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", "get", "/x", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", "GET", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", "GET", "relative", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", "GET", "/x", null))
                .isInstanceOf(NullPointerException.class);
    }
}
