package com.yuegang.zhihui.auth.domain;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Produces keyed, deterministic audit identifiers without retaining source values. */
public final class SensitiveValueHasher {
    private static final int MINIMUM_PEPPER_BYTES = 32;
    private final SecretKeySpec key;

    public SensitiveValueHasher(byte[] pepper) {
        if (pepper == null || pepper.length < MINIMUM_PEPPER_BYTES) {
            throw new IllegalArgumentException("audit pepper must contain at least 32 bytes");
        }
        this.key = new SecretKeySpec(Arrays.copyOf(pepper, pepper.length), "HmacSHA256");
    }

    public String hashPrincipal(String principal) {
        String normalized = PrincipalNormalizer.normalize(principal);
        ByteBuffer buffer = null;
        byte[] bytes = null;
        try {
            buffer = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(normalized));
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return hmac(bytes);
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("principal is not valid UTF-8", malformed);
        } finally {
            if (buffer != null && buffer.hasArray()) Arrays.fill(buffer.array(), (byte) 0);
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
        }
    }

    public String hashClientAddress(InetAddress address) {
        if (address == null) throw new IllegalArgumentException("address must not be null");
        byte[] raw = address.getAddress();
        byte[] domainSeparated = new byte[raw.length + 1];
        domainSeparated[0] = 1;
        System.arraycopy(raw, 0, domainSeparated, 1, raw.length);
        try {
            return hmac(domainSeparated);
        } finally {
            Arrays.fill(raw, (byte) 0);
            Arrays.fill(domainSeparated, (byte) 0);
        }
    }

    public String hashCaptchaAnswer(String answer) {
        if (answer == null || !answer.matches("[A-Za-z0-9]{6}")) {
            throw new IllegalArgumentException("captcha answer must contain six alphanumeric characters");
        }
        byte[] raw = answer.toUpperCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        byte[] domainSeparated = new byte[raw.length + 1];
        domainSeparated[0] = 2;
        System.arraycopy(raw, 0, domainSeparated, 1, raw.length);
        try { return hmac(domainSeparated); }
        finally {
            Arrays.fill(raw, (byte) 0);
            Arrays.fill(domainSeparated, (byte) 0);
        }
    }

    private String hmac(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 unavailable", impossible);
        }
    }

    @Override
    public String toString() {
        return "SensitiveValueHasher[pepper=[REDACTED]]";
    }
}
