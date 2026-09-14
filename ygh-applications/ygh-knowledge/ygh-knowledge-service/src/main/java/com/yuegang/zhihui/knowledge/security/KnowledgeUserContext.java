package com.yuegang.zhihui.knowledge.security;

import java.util.Set;

public record KnowledgeUserContext(long userId, Set<String> roles, Set<String> permissions,
                                   Set<String> knowledgeVisibilities) {
    public KnowledgeUserContext {
        if (userId < 0) throw new IllegalArgumentException("userId must not be negative");
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        knowledgeVisibilities = Set.copyOf(knowledgeVisibilities);
    }

    public boolean administrator() {
        return roles.contains("ADMIN");
    }

    public boolean anonymous() { return userId == 0; }
}
