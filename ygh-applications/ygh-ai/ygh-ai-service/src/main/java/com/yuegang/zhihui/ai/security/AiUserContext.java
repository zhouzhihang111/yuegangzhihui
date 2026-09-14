package com.yuegang.zhihui.ai.security;

import java.util.Set;

public record AiUserContext(long userId, Set<String> knowledgeVisibilities) {
    public AiUserContext {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        knowledgeVisibilities = Set.copyOf(knowledgeVisibilities);
        if (knowledgeVisibilities.isEmpty()) throw new IllegalArgumentException("knowledge visibility is required");
    }
}
