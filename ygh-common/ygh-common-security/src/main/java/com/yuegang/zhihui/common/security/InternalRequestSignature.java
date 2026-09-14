package com.yuegang.zhihui.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticates security-sensitive metadata propagated over the private service network. */
public final class InternalRequestSignature {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern SAFE_METHOD = Pattern.compile("[A-Z]{3,10}");
    private static final Pattern SAFE_IP = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private final SecretKeySpec key;
    private final Clock clock;
    private final Duration maximumSkew;

    public InternalRequestSignature(byte[] secret, Clock clock, Duration maximumSkew) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("internal request secret must contain at least 32 bytes");
        }
        this.key = new SecretKeySpec(Arrays.copyOf(secret, secret.length), "HmacSHA256");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maximumSkew = Objects.requireNonNull(maximumSkew, "maximumSkew must not be null");
        if (maximumSkew.compareTo(Duration.ofSeconds(1)) < 0
                || maximumSkew.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("maximumSkew must be between 1 second and 5 minutes");
        }
    }

    public String sign(Metadata metadata) {
        return HexFormat.of().formatHex(hmac(canonical(metadata)));
    }

    public boolean verify(Metadata metadata, String presentedSignature) {
        if (presentedSignature == null || !presentedSignature.matches("[0-9a-f]{64}")) return false;
        Duration skew = Duration.between(metadata.timestamp(), clock.instant()).abs();
        if (skew.compareTo(maximumSkew) > 0) return false;
        byte[] expected = hmac(canonical(metadata));
        byte[] presented;
        try {
            presented = HexFormat.of().parseHex(presentedSignature);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        try {
            return MessageDigest.isEqual(expected, presented);
        } finally {
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(presented, (byte) 0);
        }
    }

    private byte[] canonical(Metadata metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        return String.join("\n", metadata.clientIp(), metadata.traceId(), metadata.requestId(),
                metadata.method(), metadata.path(), Long.toString(metadata.timestamp().toEpochMilli()))
                .getBytes(StandardCharsets.UTF_8);
    }

    private byte[] hmac(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(value);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        } finally {
            Arrays.fill(value, (byte) 0);
        }
    }

    public record Metadata(
            String clientIp,
            String traceId,
            String requestId,
            String method,
            String path,
            Instant timestamp
    ) {
        public Metadata {
            if (clientIp == null || !SAFE_IP.matcher(clientIp).matches()) {
                throw new IllegalArgumentException("clientIp is unsafe");
            }
            if (traceId == null || !SAFE_ID.matcher(traceId).matches()
                    || requestId == null || !SAFE_ID.matcher(requestId).matches()) {
                throw new IllegalArgumentException("correlation identifier is unsafe");
            }
            if (method == null || !SAFE_METHOD.matcher(method).matches()) {
                throw new IllegalArgumentException("method is unsafe");
            }
            if (path == null || path.isBlank() || path.length() > 2048
                    || path.charAt(0) != '/' || path.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("path is unsafe");
            }
            Objects.requireNonNull(timestamp, "timestamp must not be null");
        }
    }
}
