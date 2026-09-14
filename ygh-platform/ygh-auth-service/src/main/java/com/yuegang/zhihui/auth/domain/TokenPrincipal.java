package com.yuegang.zhihui.auth.domain;

import java.util.Objects;
import java.util.Set;

public record TokenPrincipal(long accountId, long userId, Set<String> roles, Set<String> permissions) {
    public TokenPrincipal {
        if (accountId <= 0 || userId <= 0) throw new IllegalArgumentException("token identifiers must be positive");
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
        validateClaims(roles, "[A-Z][A-Z0-9_:-]{0,127}");
        validateClaims(permissions, "[A-Za-z][A-Za-z0-9:_-]{0,127}");
    }

    private static void validateClaims(Set<String> claims, String safePattern) {
        if (claims.size() > 128) throw new IllegalArgumentException("claim count exceeds limit");
        int encodedLength = 0;
        for (String claim : claims) {
            if (claim == null || !claim.matches(safePattern)) {
                throw new IllegalArgumentException("claim contains unsafe value");
            }
            encodedLength += claim.length() + (encodedLength == 0 ? 0 : 1);
        }
        if (encodedLength > 4096) throw new IllegalArgumentException("encoded claims exceed limit");
    }
}
