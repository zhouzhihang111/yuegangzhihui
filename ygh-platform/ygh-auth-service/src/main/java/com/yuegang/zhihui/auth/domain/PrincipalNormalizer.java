package com.yuegang.zhihui.auth.domain;

import java.util.Locale;

public final class PrincipalNormalizer {
    private PrincipalNormalizer() {}

    public static String normalize(String principal) {
        if (principal == null) throw new IllegalArgumentException("principal must not be null");
        String normalized = principal.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > 190
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("principal is unsafe");
        }
        return normalized;
    }
}
