package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSecurityContractTest {

    @Test
    void shouldAllowInfrastructureToProvideRevocationAndAccountState() {
        SessionRevocationStore revocations = new FakeRevocationStore();
        AccountStatusProvider accounts = userId -> !"disabled-user".equals(userId);

        revocations.revokeToken("token-1", Instant.parse("2026-07-11T09:00:00Z"));
        revocations.revokeUserSessionsIssuedBefore(
                "user-1",
                Instant.parse("2026-07-11T08:00:00Z")
        );

        assertThat(revocations.isTokenRevoked("token-1")).isTrue();
        assertThat(revocations.isUserSessionRevoked(
                "user-1",
                Instant.parse("2026-07-11T07:59:59Z")
        )).isTrue();
        assertThat(accounts.isEnabled("disabled-user")).isFalse();
    }

    private static final class FakeRevocationStore implements SessionRevocationStore {

        private String revokedToken;
        private String revokedUser;
        private Instant revokedBefore;

        @Override
        public void revokeToken(String tokenId, Instant expiresAt) {
            revokedToken = tokenId;
        }

        @Override
        public void revokeUserSessionsIssuedBefore(String userId, Instant issuedBefore) {
            revokedUser = userId;
            revokedBefore = issuedBefore;
        }

        @Override
        public boolean isTokenRevoked(String tokenId) {
            return tokenId.equals(revokedToken);
        }

        @Override
        public boolean isUserSessionRevoked(String userId, Instant issuedAt) {
            return userId.equals(revokedUser) && issuedAt.isBefore(revokedBefore);
        }
    }
}
