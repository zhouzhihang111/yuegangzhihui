package com.yuegang.zhihui.auth.application;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.yuegang.zhihui.auth.infrastructure.RsaSigningKeyRing;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class AccessTokenVerificationService {
    private final JWKSet keys;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public AccessTokenVerificationService(RsaSigningKeyRing keyRing, String issuer, String audience, Clock clock) {
        try { this.keys = JWKSet.parse(keyRing.publicJwkSet()); }
        catch (ParseException malformed) { throw new IllegalStateException("public JWK set is invalid", malformed); }
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AuthenticatedAccessToken verifyAuthorization(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 8192) {
            throw unauthenticated();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(authorization.substring(7));
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) throw unauthenticated();
            RSAKey key = keys.getKeyByKeyId(jwt.getHeader().getKeyID()) instanceof RSAKey rsa ? rsa : null;
            if (key == null || !jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))) throw unauthenticated();
            var claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            if (!issuer.equals(claims.getIssuer()) || !claims.getAudience().contains(audience)
                    || claims.getExpirationTime() == null || !claims.getExpirationTime().toInstant().isAfter(now)
                    || claims.getNotBeforeTime() != null && claims.getNotBeforeTime().toInstant().isAfter(now.plusSeconds(30))) {
                throw unauthenticated();
            }
            long accountId = Long.parseLong(claims.getStringClaim("account_id"));
            return new AuthenticatedAccessToken(accountId, claims.getJWTID(), claims.getExpirationTime().toInstant());
        } catch (BusinessException expected) {
            throw expected;
        } catch (Exception malformed) {
            throw unauthenticated();
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
    private static BusinessException unauthenticated() { return new BusinessException(ErrorCode.UNAUTHENTICATED); }
}
