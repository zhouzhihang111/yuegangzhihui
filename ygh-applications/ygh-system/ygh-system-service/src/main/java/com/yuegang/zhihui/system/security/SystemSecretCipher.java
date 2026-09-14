package com.yuegang.zhihui.system.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SystemSecretCipher {
    private static final byte[] AAD = "ygh:system:ai-provider:api-key:v1".getBytes(StandardCharsets.UTF_8);
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SystemSecretCipher(byte[] masterKey) {
        if (masterKey == null || masterKey.length != 32) {
            throw new IllegalArgumentException("system configuration master key must contain exactly 32 bytes");
        }
        this.key = new SecretKeySpec(Arrays.copyOf(masterKey, masterKey.length), "AES");
    }

    public EncryptedSecret encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) throw new IllegalArgumentException("secret is blank");
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        byte[] plain = plainText.getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(plain);
            return new EncryptedSecret(
                    Base64.getEncoder().encodeToString(encrypted),
                    Base64.getEncoder().encodeToString(nonce));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("cannot encrypt system secret", failure);
        } finally {
            Arrays.fill(plain, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    public String decrypt(String ciphertext, String encodedNonce) {
        if (ciphertext == null || encodedNonce == null) return "";
        byte[] encrypted = Base64.getDecoder().decode(ciphertext);
        byte[] nonce = Base64.getDecoder().decode(encodedNonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            byte[] plain = cipher.doFinal(encrypted);
            try {
                return new String(plain, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("cannot decrypt system secret", failure);
        } finally {
            Arrays.fill(encrypted, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    public record EncryptedSecret(String ciphertext, String nonce) {}
}
