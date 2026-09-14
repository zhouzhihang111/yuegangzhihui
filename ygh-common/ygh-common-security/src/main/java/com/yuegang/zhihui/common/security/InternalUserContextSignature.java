package com.yuegang.zhihui.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Binds Gateway-authenticated identity to one downstream request. */
public final class InternalUserContextSignature {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern SAFE_AUTHORITY = Pattern.compile("[A-Za-z][A-Za-z0-9:_-]{0,127}");
    private final SecretKeySpec key;
    private final Clock clock;
    private final Duration maximumSkew;

    public InternalUserContextSignature(byte[] secret, Clock clock, Duration maximumSkew) {
        if (secret == null || secret.length < 32) throw new IllegalArgumentException("identity secret must contain at least 32 bytes");
        this.key = new SecretKeySpec(Arrays.copyOf(secret, secret.length), "HmacSHA256");
        this.clock = Objects.requireNonNull(clock);
        this.maximumSkew = Objects.requireNonNull(maximumSkew);
        if (maximumSkew.compareTo(Duration.ofSeconds(1)) < 0 || maximumSkew.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("maximumSkew must be between 1 second and 5 minutes");
        }
    }

    public String sign(Metadata metadata) { return HexFormat.of().formatHex(hmac(canonical(metadata))); }
    public boolean verify(Metadata metadata, String signature) {
        if (signature == null || !signature.matches("[0-9a-f]{64}")) return false;
        if (Duration.between(metadata.timestamp(), clock.instant()).abs().compareTo(maximumSkew) > 0) return false;
        byte[] expected = hmac(canonical(metadata));
        byte[] actual;
        try { actual = HexFormat.of().parseHex(signature); }
        catch (IllegalArgumentException malformed) { return false; }
        try { return MessageDigest.isEqual(expected, actual); }
        finally { Arrays.fill(expected, (byte) 0); Arrays.fill(actual, (byte) 0); }
    }
    private byte[] canonical(Metadata value) {
        return String.join("\n", value.userId(), String.join(",", value.roles()),
                String.join(",", value.permissions()), value.traceId(), value.requestId(),
                value.method(), value.path(), Long.toString(value.timestamp().toEpochMilli()))
                .getBytes(StandardCharsets.UTF_8);
    }
    private byte[] hmac(byte[] value) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(key); return mac.doFinal(value); }
        catch (GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
        finally { Arrays.fill(value, (byte) 0); }
    }

    public record Metadata(String userId, List<String> roles, List<String> permissions,
            String traceId, String requestId, String method, String path, Instant timestamp) {
        public Metadata {
            requireSafe(userId, SAFE_ID, "userId");
            roles = normalized(roles, "roles");
            permissions = normalized(permissions, "permissions");
            requireSafe(traceId, SAFE_ID, "traceId");
            requireSafe(requestId, SAFE_ID, "requestId");
            if (method == null || !method.matches("[A-Z]{3,10}")) throw new IllegalArgumentException("method is unsafe");
            if (path == null || path.isBlank() || path.length() > 2048 || path.charAt(0) != '/') {
                throw new IllegalArgumentException("path is unsafe");
            }
            Objects.requireNonNull(timestamp);
        }
        private static List<String> normalized(List<String> values, String name) {
            Objects.requireNonNull(values, name);
            if (values.size() > 128) throw new IllegalArgumentException(name + " exceeds limit");
            var copy = values.stream().peek(v -> requireSafe(v, SAFE_AUTHORITY, name)).sorted().distinct().toList();
            if (String.join(",", copy).length() > 4096) throw new IllegalArgumentException(name + " exceeds length limit");
            return copy;
        }
        private static void requireSafe(String value, Pattern pattern, String name) {
            if (value == null || !pattern.matcher(value).matches()) throw new IllegalArgumentException(name + " is unsafe");
        }
    }
}
