package com.yuegang.zhihui.auth.domain;

import java.util.Optional;

public interface AccountSecurityRepository {
    Optional<AccountSecuritySnapshot> findById(long accountId);
    boolean compareAndSetAccessState(
            long accountId, long expectedVersion, AccountAccessState newState);
}
