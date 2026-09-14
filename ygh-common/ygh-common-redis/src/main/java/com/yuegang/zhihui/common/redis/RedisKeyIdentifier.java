package com.yuegang.zhihui.common.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Creates deterministic opaque identifiers for values that must not appear in Redis keys. */
public final class RedisKeyIdentifier {

    private RedisKeyIdentifier() {
    }

    public static String sha256(String value) {
        requireValue(value);
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Hides low-entropy identifiers such as email addresses using an environment Secret pepper. */
    public static String hmacSha256(String value, byte[] pepper) {
        requireValue(value);
        if (pepper == null || pepper.length < 32) {
            throw new IllegalArgumentException("pepper must contain at least 32 bytes");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.clone(), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private static void requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
