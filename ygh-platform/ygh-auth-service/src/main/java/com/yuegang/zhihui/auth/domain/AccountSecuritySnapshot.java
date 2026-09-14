package com.yuegang.zhihui.auth.domain;

public record AccountSecuritySnapshot(long accountId, AccountAccessState accessState, long version) {
    public AccountSecuritySnapshot {
        if (accountId <= 0 || version < 0) {
            throw new IllegalArgumentException("account security snapshot identifiers are invalid");
        }
    }
}
