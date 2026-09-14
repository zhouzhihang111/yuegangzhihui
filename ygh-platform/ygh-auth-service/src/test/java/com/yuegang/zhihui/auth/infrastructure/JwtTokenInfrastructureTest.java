package com.yuegang.zhihui.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import com.yuegang.zhihui.auth.domain.TokenPrincipal;
import com.yuegang.zhihui.auth.domain.AccessToken;
import com.yuegang.zhihui.auth.application.SessionAwareAccessTokenIssuer;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtTokenInfrastructureTest {
    @TempDir Path keyDirectory;

    @Test
    void publishesCurrentAndPreviousPublicKeysButSignsOnlyWithActivePrivateKey() throws Exception {
        KeyPair previous = keyPair(2048);
        KeyPair active = keyPair(2048);
        writePublic("2026-06", previous);
        writePublic("2026-07", active);
        writePrivate("2026-07", active);

        var ring = RsaSigningKeyRing.load(keyDirectory, "2026-07");
        var issuer = new NimbusAccessTokenIssuer(
                ring, "https://auth.ygh.test", "ygh-api", Duration.ofMinutes(15),
                Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC));
        var token = issuer.issue(new TokenPrincipal(7, 42, Set.of("USER"), Set.of("order:read")));
        SignedJWT jwt = SignedJWT.parse(token.value());

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("2026-07");
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) active.getPublic()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("https://auth.ygh.test");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("ygh-api");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("42");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("account_id")).isEqualTo("7");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("USER");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("permissions")).containsExactly("order:read");
        assertThat(token.expiresAt()).isEqualTo(Instant.parse("2026-07-12T00:15:00Z"));
        assertThat(token.toString()).doesNotContain(token.value());

        var jwks = JWKSet.parse(ring.publicJwkSet());
        assertThat(jwks.getKeys()).extracting(key -> key.getKeyID())
                .containsExactly("2026-06", "2026-07");
        assertThat(jwks.getKeys()).allMatch(key -> !key.isPrivate());
    }

    @Test
    void rejectsWeakMismatchedOrMissingActiveKeys() throws Exception {
        KeyPair weak = keyPair(1024);
        writePublic("weak", weak);
        writePrivate("weak", weak);
        assertThatThrownBy(() -> RsaSigningKeyRing.load(keyDirectory, "weak"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("weaker");

        Files.delete(keyDirectory.resolve("weak.public.pem"));
        Files.delete(keyDirectory.resolve("weak.private.pem"));
        KeyPair first = keyPair(2048);
        KeyPair second = keyPair(2048);
        writePublic("active", first);
        writePrivate("active", second);
        assertThatThrownBy(() -> RsaSigningKeyRing.load(keyDirectory, "active"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not match");
    }

    @Test
    void rejectsPrivateKeyReadableByGroupOrOthersOnPosix() throws Exception {
        if (Files.getFileAttributeView(keyDirectory,
                java.nio.file.attribute.PosixFileAttributeView.class) == null) return;
        KeyPair active = keyPair(2048);
        writePublic("active", active);
        writePrivate("active", active);
        Files.setPosixFilePermissions(keyDirectory.resolve("active.private.pem"), java.util.Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.GROUP_READ));
        assertThatThrownBy(() -> RsaSigningKeyRing.load(keyDirectory, "active"))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("JWT private key permissions are too broad");
    }

    @Test
    void sessionAwareIssuerRegistersEveryJwtBeforeReturningIt() {
        Instant now = Instant.parse("2026-07-12T00:00:00Z");
        var recorded = new java.util.ArrayList<String>();
        SessionStateStore sessions = new SessionStateStore() {
            @Override public void register(long accountId, String jwtId, Instant expiresAt, Instant registeredAt) {
                recorded.add(accountId + ":" + jwtId + ":" + expiresAt + ":" + registeredAt);
            }
            @Override public void revoke(long accountId, String jwtId, Instant expiresAt, Instant registeredAt) { }
            @Override public void disableAccount(long accountId) { }
            @Override public void enableAccount(long accountId) { }
        };
        var issuer = new SessionAwareAccessTokenIssuer(
                ignored -> new AccessToken("signed-value", "jwt-1", now.plusSeconds(900)),
                sessions, Clock.fixed(now, ZoneOffset.UTC));

        var issued = issuer.issue(new TokenPrincipal(7, 42, Set.of("USER"), Set.of("order:read")));

        assertThat(issued.jwtId()).isEqualTo("jwt-1");
        assertThat(recorded).containsExactly("7:jwt-1:2026-07-12T00:15:00Z:2026-07-12T00:00:00Z");
    }

    private KeyPair keyPair(int bits) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private void writePublic(String kid, KeyPair pair) throws Exception {
        writePem(keyDirectory.resolve(kid + ".public.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());
    }

    private void writePrivate(String kid, KeyPair pair) throws Exception {
        Path path = keyDirectory.resolve(kid + ".private.pem");
        writePem(path, "PRIVATE KEY", pair.getPrivate().getEncoded());
        if (Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(keyDirectory, java.util.Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
            Files.setPosixFilePermissions(path, java.util.Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        }
    }

    private void writePem(Path path, String type, byte[] encoded) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
    }
}
