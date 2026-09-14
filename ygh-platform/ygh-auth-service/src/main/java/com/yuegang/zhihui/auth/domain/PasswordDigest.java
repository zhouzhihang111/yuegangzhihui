package com.yuegang.zhihui.auth.domain;

import java.util.Objects;

public record PasswordDigest(String hash, String algorithm, int version) {
    public PasswordDigest {
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        if (hash.isBlank() || algorithm.isBlank() || version < 1) {
            throw new IllegalArgumentException("password digest metadata is invalid");
        }
    }

    @Override
    public String toString() {
        return "PasswordDigest[algorithm=" + algorithm + ", version=" + version + ", hash=[REDACTED]]";
    }
}
