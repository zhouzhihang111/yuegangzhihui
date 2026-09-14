package com.yuegang.zhihui.auth.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.yuegang.zhihui.auth.domain.AccessToken;
import com.yuegang.zhihui.auth.domain.AccessTokenIssuer;
import com.yuegang.zhihui.auth.domain.TokenPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public final class NimbusAccessTokenIssuer implements AccessTokenIssuer {
    private final RsaSigningKeyRing keyRing;
    private final String issuer;
    private final String audience;
    private final Duration lifetime;
    private final Clock clock;

    public NimbusAccessTokenIssuer(
            RsaSigningKeyRing keyRing, String issuer, String audience, Duration lifetime, Clock clock) {
        this.keyRing = Objects.requireNonNull(keyRing, "keyRing must not be null");
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime must not be null");
        if (lifetime.compareTo(Duration.ofMinutes(5)) < 0 || lifetime.compareTo(Duration.ofMinutes(20)) > 0) {
            throw new IllegalArgumentException("access-token lifetime must be between 5 and 20 minutes");
        }
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public AccessToken issue(TokenPrincipal principal) {
        Objects.requireNonNull(principal, "principal must not be null");
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(lifetime);
        String jwtId = UUID.randomUUID().toString();
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer).audience(audience).subject(Long.toString(principal.userId()))
                .jwtID(jwtId).issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt)).expirationTime(Date.from(expiresAt))
                .claim("account_id", Long.toString(principal.accountId()))
                .claim("roles", principal.roles().stream().sorted().toList())
                .claim("permissions", principal.permissions().stream().sorted().toList()).build();
        var key = keyRing.activeSigningKey();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID()).type(com.nimbusds.jose.JOSEObjectType.JWT).build(), claims);
        try {
            jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
            return new AccessToken(jwt.serialize(), jwtId, expiresAt);
        } catch (JOSEException signingFailure) {
            throw new IllegalStateException("access token signing failed", signingFailure);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
