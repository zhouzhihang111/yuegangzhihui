package com.yuegang.zhihui.common.redis;

import java.security.SecureRandom;
import java.util.Base64;

/** Uses 192 bits of randomness without embedding host, process or user information. */
public final class SecureLockOwnerTokenGenerator implements LockOwnerTokenGenerator {

    private static final int TOKEN_BYTES = 24;
    private final SecureRandom secureRandom;

    public SecureLockOwnerTokenGenerator() {
        this(new SecureRandom());
    }

    SecureLockOwnerTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = java.util.Objects.requireNonNull(
                secureRandom, "secureRandom must not be null");
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
