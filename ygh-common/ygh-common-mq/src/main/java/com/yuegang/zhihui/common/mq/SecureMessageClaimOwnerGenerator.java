package com.yuegang.zhihui.common.mq;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** Creates a per-attempt owner with 192 bits of cryptographic randomness. */
public final class SecureMessageClaimOwnerGenerator implements MessageClaimOwnerGenerator {

    private final SecureRandom random;

    public SecureMessageClaimOwnerGenerator() {
        this(new SecureRandom());
    }

    SecureMessageClaimOwnerGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
