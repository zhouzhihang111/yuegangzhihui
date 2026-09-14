package com.yuegang.zhihui.training.security;

import java.util.Set;

public record TrainingUserContext(long userId, Set<String> roles, Set<String> permissions) {
    public TrainingUserContext {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean courseManager() {
        return roles.contains("ADMIN") || permissions.contains("training:course:write")
                || permissions.contains("training:course:publish");
    }
}
