package com.yuegang.zhihui.auth.domain;

import java.util.Optional;

@FunctionalInterface
public interface LoginAccountRepository {
    Optional<LoginAccount> findByPrincipal(String normalizedPrincipal);
    default Optional<LoginAccount> findByAccountId(long accountId) {
        throw new UnsupportedOperationException("findByAccountId is not implemented");
    }
    default LoginAccount create(long accountId, long userId, String normalizedPrincipal,
            String accountType, PasswordDigest passwordDigest) {
        throw new UnsupportedOperationException("create is not implemented");
    }
}
