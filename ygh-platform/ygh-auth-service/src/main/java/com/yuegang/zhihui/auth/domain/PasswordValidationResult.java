package com.yuegang.zhihui.auth.domain;

import java.util.Set;

public record PasswordValidationResult(Set<PasswordViolation> violations) {
    public PasswordValidationResult {
        violations = Set.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }

    @Override
    public String toString() {
        return "PasswordValidationResult[violations=" + violations + ']';
    }
}
