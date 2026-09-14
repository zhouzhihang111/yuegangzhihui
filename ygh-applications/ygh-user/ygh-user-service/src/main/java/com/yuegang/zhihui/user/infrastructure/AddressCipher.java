package com.yuegang.zhihui.user.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** AES-256-GCM field encryption. The IV is randomly generated and prefixed to every value. */
public final class AddressCipher {
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final int keyVersion;
    private final SecureRandom random;

    public AddressCipher(String keyBase64, int keyVersion) { this(keyBase64, keyVersion, new SecureRandom()); }
    AddressCipher(String keyBase64, int keyVersion, SecureRandom random) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(Objects.requireNonNull(keyBase64)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("PII key must be valid Base64", invalid); }
        if (decoded.length != 32) throw new IllegalArgumentException("PII key must contain exactly 32 bytes");
        if (keyVersion < 1 || keyVersion > 65535) throw new IllegalArgumentException("PII key version is invalid");
        this.key = new SecretKeySpec(decoded, "AES");
        Arrays.fill(decoded, (byte) 0);
        this.keyVersion = keyVersion;
        this.random = Objects.requireNonNull(random);
    }
    public int keyVersion() { return keyVersion; }
    public byte[] encrypt(long userId, String field, String value) {
        byte[] iv = new byte[IV_BYTES]; random.nextBytes(iv);
        byte[] plain = value.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(userId, field, keyVersion));
            return ByteBuffer.allocate(iv.length + cipher.getOutputSize(plain.length)).put(iv).put(cipher.doFinal(plain)).array();
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("PII encryption failed", failure); }
        finally { Arrays.fill(plain, (byte) 0); }
    }
    public String decrypt(long userId, String field, int storedVersion, byte[] value) {
        if (storedVersion < 1 || storedVersion > 65535 || value == null || value.length <= IV_BYTES + 16)
            throw new IllegalStateException("PII ciphertext or key version is invalid");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, value, 0, IV_BYTES));
            cipher.updateAAD(aad(userId, field, storedVersion));
            byte[] plain = cipher.doFinal(value, IV_BYTES, value.length - IV_BYTES);
            try { return new String(plain, StandardCharsets.UTF_8); }
            finally { Arrays.fill(plain, (byte) 0); }
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("PII decryption failed", failure); }
    }
    private static byte[] aad(long userId, String field, int version) {
        return (userId + ":" + field + ":" + version).getBytes(StandardCharsets.US_ASCII);
    }
}
