package com.yuegang.zhihui.common.security;

import java.time.Instant;

/**
 * Storage-agnostic contract for token blacklisting and user-wide session
 * revocation. The Redis implementation belongs to common-redis/Auth.
 */
public interface SessionRevocationStore {

    void revokeToken(String tokenId, Instant expiresAt);

    void revokeUserSessionsIssuedBefore(String userId, Instant issuedBefore);

    boolean isTokenRevoked(String tokenId);

    boolean isUserSessionRevoked(String userId, Instant issuedAt);
}
