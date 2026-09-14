package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

class GatewayJwtDecoderTest {

    private static final String ISSUER = "https://auth.example.test";
    private static final String AUDIENCE = "ygh-api";
    private static KeyPair trustedKey;
    private static KeyPair attackerKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        trustedKey = generator.generateKeyPair();
        attackerKey = generator.generateKeyPair();
    }

    @Test
    void acceptsValidRsaSignedAccessToken() throws Exception {
        Instant now = Instant.now();
        String token = signedToken(
                trustedKey,
                now.minusSeconds(5),
                now.plusSeconds(60),
                ISSUER,
                AUDIENCE);

        var jwt = decoder().decode(token).block();

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("user-1001");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaimAsStringList("permissions"))
                .containsExactly("user:profile:read");
    }

    @Test
    void rejectsExpiredAccessToken() throws Exception {
        Instant now = Instant.now();
        String token = signedToken(
                trustedKey,
                now.minusSeconds(120),
                now.minusSeconds(60),
                ISSUER,
                AUDIENCE);

        assertThatThrownBy(() -> decoder().decode(token).block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedByUntrustedKey() throws Exception {
        Instant now = Instant.now();
        String token = signedToken(
                attackerKey,
                now.minusSeconds(5),
                now.plusSeconds(60),
                ISSUER,
                AUDIENCE);

        assertThatThrownBy(() -> decoder().decode(token).block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuerOrAudience() throws Exception {
        Instant now = Instant.now();
        String wrongIssuer = signedToken(
                trustedKey,
                now.minusSeconds(5),
                now.plusSeconds(60),
                "https://attacker.example.test",
                AUDIENCE);
        String wrongAudience = signedToken(
                trustedKey,
                now.minusSeconds(5),
                now.plusSeconds(60),
                ISSUER,
                "other-api");

        assertThatThrownBy(() -> decoder().decode(wrongIssuer).block())
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder().decode(wrongAudience).block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validatorFactoryRejectsBlankConfiguration() {
        assertThatThrownBy(() -> GatewayJwtValidators.create(null, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> GatewayJwtValidators.create(" ", AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer");
        assertThatThrownBy(() -> GatewayJwtValidators.create(ISSUER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
        assertThatThrownBy(() -> GatewayJwtValidators.create(ISSUER, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
    }

    private static NimbusReactiveJwtDecoder decoder() {
        var decoder = NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) trustedKey.getPublic())
                .build();
        decoder.setJwtValidator(GatewayJwtValidators.create(ISSUER, AUDIENCE));
        return decoder;
    }

    private static String signedToken(
            KeyPair keyPair,
            Instant issuedAt,
            Instant expiresAt,
            String issuer,
            String audience
    ) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .jwtID("token-1001")
                .subject("user-1001")
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .notBeforeTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .claim("roles", List.of("CUSTOMER"))
                .claim("permissions", List.of("user:profile:read"))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }
}
