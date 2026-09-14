package com.yuegang.zhihui.auth.domain;

import java.util.Optional;
import java.time.OffsetDateTime;

public interface AccountAdministrationRepository {
    Optional<StatusChange> changeStatus(long userId, AccountStatus status, long expectedVersion,
            long operatorUserId, String reason);
    record StatusChange(long accountId,long userId,AccountStatus status,long version,OffsetDateTime updatedAt) { }
}
