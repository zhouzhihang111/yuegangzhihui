package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.domain.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class OpaqueRefreshTokenService {
    private final RefreshTokenRepository repository;
    private final SecureRandom random;
    private final Clock clock;
    private final Duration lifetime;

    public OpaqueRefreshTokenService(RefreshTokenRepository repository, Clock clock, Duration lifetime) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime must not be null");
        if (lifetime.compareTo(Duration.ofDays(1)) < 0 || lifetime.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("refresh-token lifetime must be between 1 and 30 days");
        }
        this.random = new SecureRandom();
    }

    public RefreshTokenPair issueInitial(long accountId) {
        if (accountId <= 0) throw new IllegalArgumentException("accountId must be positive");
        GeneratedToken generated = generate();
        repository.insertInitial(accountId, UUID.randomUUID().toString(), generated.stored());
        return generated.pair();
    }

    public RotatedRefreshToken rotate(char[] presentedToken) {
        String hash = hash(presentedToken);
        GeneratedToken replacement = generate();
        RefreshRotationResult result = repository.rotate(hash, replacement.stored(), clock.instant());
        return new RotatedRefreshToken(result, result.status() == RefreshRotationStatus.ROTATED ? replacement.pair() : null);
    }

    public void revoke(char[] presentedToken, String reason) {
        if (reason == null || !reason.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("revoke reason is unsafe");
        }
        repository.revokeFamilyByTokenHash(hash(presentedToken), clock.instant(), reason);
    }

    private GeneratedToken generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        try {
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            char[] rawCharacters = raw.toCharArray();
            Instant issuedAt = clock.instant();
            Instant expiresAt = issuedAt.plus(lifetime);
            long id = random.nextLong(1, Long.MAX_VALUE);
            try {
                return new GeneratedToken(
                        new RefreshTokenPair(raw, expiresAt),
                        new NewRefreshToken(id, hash(rawCharacters), issuedAt, expiresAt));
            } finally {
                Arrays.fill(rawCharacters, '\0');
            }
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private String hash(char[] token) {
        Objects.requireNonNull(token, "token must not be null");
        ByteBuffer buffer = null;
        byte[] encoded = null;
        try {
            buffer = StandardCharsets.US_ASCII.newEncoder().encode(CharBuffer.wrap(token));
            encoded = new byte[buffer.remaining()];
            buffer.get(encoded);
            return HexFormat.of().formatHex(sha256(encoded));
        } catch (CharacterCodingException malformed) {
            return "0".repeat(64);
        } finally {
            if (buffer != null && buffer.hasArray()) Arrays.fill(buffer.array(), (byte) 0);
            if (encoded != null) Arrays.fill(encoded, (byte) 0);
        }
    }

    private byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private record GeneratedToken(RefreshTokenPair pair, NewRefreshToken stored) {}

    public record RotatedRefreshToken(RefreshRotationResult result, RefreshTokenPair replacement) {
        @Override public String toString() { return "RotatedRefreshToken[result=" + result.status() + ", replacement=[REDACTED]]"; }
    }
}
