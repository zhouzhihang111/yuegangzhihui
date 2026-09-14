package com.yuegang.zhihui.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InternalRequestSignatureTest {
    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private final InternalRequestSignature signatures = new InternalRequestSignature(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII),
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));

    @Test
    void verifiesOnlyUntamperedMetadataInsideTheTimeWindow() {
        var metadata = metadata(NOW);
        String signature = signatures.sign(metadata);

        assertThat(signatures.verify(metadata, signature)).isTrue();
        assertThat(signatures.verify(new InternalRequestSignature.Metadata(
                "192.0.2.9", metadata.traceId(), metadata.requestId(), metadata.method(),
                metadata.path(), metadata.timestamp()), signature)).isFalse();
        assertThat(signatures.verify(metadata(NOW.minusSeconds(31)),
                signatures.sign(metadata(NOW.minusSeconds(31))))).isFalse();
        assertThat(signatures.verify(metadata, "not-a-signature")).isFalse();
    }

    private static InternalRequestSignature.Metadata metadata(Instant timestamp) {
        return new InternalRequestSignature.Metadata(
                "192.0.2.8", "trace-1", "request-1", "POST", "/api/v1/auth/login", timestamp);
    }
}
