package com.yuegang.zhihui.common.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable authenticated user information propagated inside trusted services.
 * The user id remains a string to avoid precision loss at JavaScript clients and
 * to keep the model compatible with non-numeric identity providers.
 */
public record CurrentUserPrincipal(
        String userId,
        Set<String> roles,
        Set<String> permissions
) {

    public CurrentUserPrincipal {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        roles = immutableCopy(roles, "roles");
        permissions = immutableCopy(permissions, "permissions");
    }

    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }

    private static Set<String> immutableCopy(Set<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        LinkedHashSet<String> copy = new LinkedHashSet<>(values.size());
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not contain blank values");
            }
            copy.add(value);
        }
        return Collections.unmodifiableSet(copy);
    }
}
