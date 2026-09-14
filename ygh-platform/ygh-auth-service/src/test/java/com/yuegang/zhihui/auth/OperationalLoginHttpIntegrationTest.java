package com.yuegang.zhihui.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import com.yuegang.zhihui.auth.domain.Argon2PasswordHasher;
import com.yuegang.zhihui.auth.domain.AuthorityProvider;
import com.yuegang.zhihui.auth.domain.SensitiveValueHasher;
import com.yuegang.zhihui.common.redis.RedisKeyBuilder;
import com.yuegang.zhihui.common.redis.SessionRedisKeys;
import com.yuegang.zhihui.common.security.InternalRequestSignature;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OperationalLoginHttpIntegrationTest {
    private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    @TempDir Path keyDirectory;

    @Test
    void realHttpLoginPersistsAuditAndCreatesRefreshJwtAndRedisSession() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            var redis = YghTestContainerFactory.redis();
            redis.start();
            try {
                Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                        .locations("classpath:db/migration").load().migrate();
                char[] password = "Correct Horse Battery 2026".toCharArray();
                var digest = Argon2PasswordHasher.owaspMinimum().hash(password);
                java.util.Arrays.fill(password, '\0');
                insertAccount(mysql.jdbcUrl(), mysql.username(), mysql.credential(), digest.hash());
                writeKeyPair("login-test");

                try (var context = new SpringApplicationBuilder(AuthApplication.class, AuthorityStubConfiguration.class)
                        .web(WebApplicationType.SERVLET)
                        .properties(
                                "YGH_AUTH_PORT=0",
                                "spring.cloud.nacos.discovery.enabled=false",
                                "YGH_AUTH_DB_URL=" + mysql.jdbcUrl(),
                                "YGH_AUTH_DB_APP_USERNAME=" + mysql.username(),
                                "YGH_AUTH_DB_APP_PASSWORD=" + mysql.credential(),
                                "YGH_AUTH_DB_MIGRATION_USERNAME=" + mysql.username(),
                                "YGH_AUTH_DB_MIGRATION_PASSWORD=" + mysql.credential(),
                                "YGH_NACOS_SERVER_ADDR=127.0.0.1:1",
                                "YGH_NACOS_USERNAME=test",
                                "YGH_NACOS_PASSWORD=test",
                                "YGH_REDIS_HOST=" + redis.getHost(),
                                "YGH_REDIS_PORT=" + redis.getMappedPort(6379),
                                "YGH_REDIS_PASSWORD=",
                                "YGH_REDIS_ENVIRONMENT=test",
                                "YGH_AUTH_AUDIT_PEPPER_BASE64=" + TEST_SECRET,
                                "YGH_INTERNAL_REQUEST_HMAC_BASE64=" + TEST_SECRET,
                                "YGH_SYSTEM_INTERNAL_BASE_URL=http://127.0.0.1:1",
                                "YGH_JWT_ENABLED=true",
                                "YGH_AUTH_ID_WORKER=1",
                                "YGH_JWT_KEY_DIRECTORY=" + keyDirectory.toAbsolutePath(),
                                "YGH_JWT_ACTIVE_KID=login-test",
                                "YGH_JWT_ISSUER=https://auth.integration.test")
                        .run()) {
                    assertThat(context.getBean(com.yuegang.zhihui.auth.application.AuthCommandService.class)
                            .getClass().getSimpleName()).isEqualTo("OperationalAuthCommandService");
                    int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
                    verifyOpenApi(port);
                    LoginResult result = login(port);
                    SignedJWT jwt = SignedJWT.parse(result.accessToken);
                    assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("84");
                    assertThat(jwt.getJWTClaimsSet().getStringClaim("account_id")).isEqualTo("42");
                    assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("CUSTOMER");
                    assertThat(result.refreshToken).isNotBlank();
                    verifyDatabase(mysql.jdbcUrl(), mysql.username(), mysql.credential());
                    verifyRedis(redis.getHost(), redis.getMappedPort(6379), jwt.getJWTClaimsSet().getJWTID());
                    verifyRegistrationRefreshReplayLogoutAndLock(port, redis.getHost(), redis.getMappedPort(6379));
                }
            } finally {
                redis.stop();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AuthorityStubConfiguration {
        @Bean
        @Primary
        AuthorityProvider testAuthorityProvider() {
            return userId -> new AuthorityProvider.Authorities(Set.of("CUSTOMER"), Set.of());
        }
    }

    private static void verifyOpenApi(int port) throws Exception {
        JsonNode api = get(port, "/v3/api-docs");
        assertThat(api.path("info").path("title").asString()).isEqualTo("YGH Authentication API");
        assertThat(api.path("paths").size()).isEqualTo(11);
        assertThat(api.path("servers").get(0).path("url").asString()).isEqualTo("/");
        assertThat(api.path("paths").path("/api/v1/auth/register").path("post")
                .path("responses").has("201")).isTrue();
        assertThat(api.path("paths").path("/api/v1/auth/password-reset/request").path("post")
                .path("responses").has("202")).isTrue();
        JsonNode logout = api.path("paths").path("/api/v1/auth/logout").path("post");
        assertThat(logout.path("security").toString()).contains("bearerAuth");
        assertThat(logout.path("responses").has("401")).isTrue();
        assertThat(logout.path("responses").has("503")).isTrue();
        JsonNode administration = api.path("paths").path("/api/v1/auth/admin/users/{userId}/status").path("put");
        assertThat(administration.path("security").toString()).contains("bearerAuth");
        assertThat(administration.path("responses").has("403")).isTrue();
        assertThat(api.path("components").path("schemas").has("ApiResponse")).isTrue();
    }

    private void verifyRegistrationRefreshReplayLogoutAndLock(int port, String redisHost, int redisPort) throws Exception {
        JsonNode captcha = get(port, "/api/v1/auth/captcha").path("data");
        byte[] captchaImage = Base64.getDecoder().decode(captcha.path("imageBase64").asString());
        assertThat(captcha.path("mimeType").asString()).isEqualTo("image/png");
        assertThat(captchaImage).startsWith(0x89, 0x50, 0x4e, 0x47);
        String registrationChallenge = "integrationcaptcha1";
        String registrationAnswer = "ABC234";
        seedCaptcha(redisHost, redisPort, registrationChallenge, registrationAnswer);
        String registerBody = """
                {"principal":"new.customer@example.com","password":"Unique enterprise phrase 2026!",
                 "confirmPassword":"Unique enterprise phrase 2026!","captchaChallengeId":"%s",
                 "captchaAnswer":"%s","agreementAccepted":true}
                """.formatted(registrationChallenge, registrationAnswer);
        HttpResponse<String> registered = post(port, "/api/v1/auth/register", registerBody);
        assertThat(registered.statusCode()).isEqualTo(201);
        JsonNode registeredJson = JsonMapper.builder().build().readTree(registered.body());
        String registeredRefresh = registeredJson.path("data").path("tokens").path("refreshToken").asString();
        assertThat(registeredRefresh).isNotBlank();

        HttpResponse<String> refreshed = post(port, "/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + registeredRefresh + "\",\"deviceId\":\"browser\"}");
        assertThat(refreshed.statusCode()).isEqualTo(200);
        String replacement = JsonMapper.builder().build().readTree(refreshed.body())
                .path("data").path("refreshToken").asString();
        assertThat(replacement).isNotBlank().isNotEqualTo(registeredRefresh);

        HttpResponse<String> replay = post(port, "/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + registeredRefresh + "\",\"deviceId\":\"browser\"}");
        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(JsonMapper.builder().build().readTree(replay.body()).path("code").asString())
                .isEqualTo("UNAUTHENTICATED");

        assertThat(post(port, "/api/v1/auth/logout",
                "{\"refreshToken\":\"" + replacement + "\"}").statusCode()).isEqualTo(401);
        String registeredAccess = registeredJson.path("data").path("tokens").path("accessToken").asString();
        HttpResponse<String> logout = postAuthorized(port, "/api/v1/auth/logout",
                "{\"refreshToken\":\"" + replacement + "\"}", registeredAccess);
        assertThat(logout.statusCode()).isEqualTo(200);
        SignedJWT registeredJwt = SignedJWT.parse(registeredAccess);
        verifyRevokedSession(redisHost, redisPort,
                Long.parseLong(registeredJwt.getJWTClaimsSet().getStringClaim("account_id")),
                registeredJwt.getJWTClaimsSet().getJWTID());
        assertThat(post(port, "/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + replacement + "\",\"deviceId\":\"browser\"}").statusCode())
                .isEqualTo(401);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(loginResponse(port, "Wrong enterprise passphrase", "lock-" + attempt).statusCode())
                    .isEqualTo(401);
        }
        assertThat(loginResponse(port, "Correct Horse Battery 2026", "locked-correct").statusCode())
                .isEqualTo(401);
    }

    private static JsonNode get(int port, String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JsonMapper.builder().build().readTree(response.body());
    }

    private static HttpResponse<String> post(int port, String path, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postAuthorized(int port, String path, String body, String access) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + access)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> loginResponse(int port, String password, String suffix) throws Exception {
        Instant now = Instant.now();
        String traceId = "trace-http-" + suffix;
        String requestId = "request-http-" + suffix;
        var signatures = new InternalRequestSignature(Base64.getDecoder().decode(TEST_SECRET),
                Clock.systemUTC(), Duration.ofSeconds(30));
        var metadata = new InternalRequestSignature.Metadata(
                "192.0.2.89", traceId, requestId, "POST", "/api/v1/auth/login", now);
        String body = "{\"principal\":\"alice@example.com\",\"password\":\"" + password
                + "\",\"deviceId\":\"test\"}";
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json").header("X-Trace-Id", traceId)
                .header("X-Request-Id", requestId).header("X-YGH-Client-IP", "192.0.2.89")
                .header("X-YGH-Client-IP-Timestamp", Long.toString(now.toEpochMilli()))
                .header("X-YGH-Client-IP-Signature", signatures.sign(metadata))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private LoginResult login(int port) throws Exception {
        Instant now = Instant.now();
        String traceId = "trace-http-login";
        String requestId = "request-http-login";
        var signatures = new InternalRequestSignature(Base64.getDecoder().decode(TEST_SECRET),
                Clock.systemUTC(), Duration.ofSeconds(30));
        var metadata = new InternalRequestSignature.Metadata(
                "192.0.2.88", traceId, requestId, "POST", "/api/v1/auth/login", now);
        String body = """
                {"principal":"alice@example.com","password":"Correct Horse Battery 2026","deviceId":"test"}
                """;
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", traceId)
                .header("X-Request-Id", requestId)
                .header("X-YGH-Client-IP", "192.0.2.88")
                .header("X-YGH-Client-IP-Timestamp", Long.toString(now.toEpochMilli()))
                .header("X-YGH-Client-IP-Signature", signatures.sign(metadata))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = JsonMapper.builder().build().readTree(response.body());
        assertThat(json.path("code").asString()).isEqualTo("SUCCESS");
        assertThat(json.path("traceId").asString()).isEqualTo(traceId);
        return new LoginResult(
                json.path("data").path("tokens").path("accessToken").asString(),
                json.path("data").path("tokens").path("refreshToken").asString());
    }

    private static void insertAccount(String url, String username, String credential, String hash) throws Exception {
        try (var connection = DriverManager.getConnection(url, username, credential)) {
            try (var account = connection.prepareStatement("""
                    INSERT INTO auth_account (id,user_id,principal,account_type,status)
                    VALUES (42,84,'alice@example.com','CUSTOMER','ACTIVE')
                    """)) { account.executeUpdate(); }
            try (var stored = connection.prepareStatement("""
                    INSERT INTO auth_credential
                      (id,account_id,password_hash,password_algorithm,password_version,changed_at)
                    VALUES (43,42,?,'ARGON2ID',1,?)
                    """)) {
                stored.setString(1, hash);
                stored.setTimestamp(2, Timestamp.from(Instant.now()));
                stored.executeUpdate();
            }
        }
    }

    private static void verifyDatabase(String url, String username, String credential) throws Exception {
        try (var connection = DriverManager.getConnection(url, username, credential)) {
            try (var rows = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM auth_refresh_token WHERE account_id=42 AND revoked_at IS NULL")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
            }
            try (var rows = connection.createStatement().executeQuery("""
                    SELECT result, principal_hash, client_ip_hash FROM auth_login_attempt WHERE account_id=42
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("result")).isEqualTo("SUCCESS");
                assertThat(rows.getString("principal_hash")).matches("[0-9a-f]{64}");
                assertThat(rows.getString("client_ip_hash")).matches("[0-9a-f]{64}");
            }
        }
    }

    private static void verifyRedis(String host, int port, String jwtId) {
        var connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        try {
            var redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            var keys = new SessionRedisKeys(new RedisKeyBuilder(), "test");
            assertThat(redis.opsForValue().get(keys.accountState(42))).isEqualTo("ACTIVE");
            assertThat(redis.opsForValue().get(keys.session(42, jwtId))).isEqualTo("42");
        } finally {
            connectionFactory.destroy();
        }
    }

    private static void verifyRevokedSession(String host, int port, long accountId, String jwtId) {
        var connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        try {
            var redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            var keys = new SessionRedisKeys(new RedisKeyBuilder(), "test");
            assertThat(redis.opsForValue().get(keys.session(accountId, jwtId))).isNull();
            assertThat(redis.opsForValue().get(keys.revoked(accountId, jwtId))).isEqualTo("1");
        } finally { connectionFactory.destroy(); }
    }

    private static void seedCaptcha(String host, int port, String challengeId, String answer) {
        var connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        try {
            var redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            String key = new RedisKeyBuilder().build("test", "auth", "captcha", challengeId);
            String hash = new SensitiveValueHasher(Base64.getDecoder().decode(TEST_SECRET)).hashCaptchaAnswer(answer);
            redis.opsForValue().set(key, hash, Duration.ofMinutes(5));
        } finally { connectionFactory.destroy(); }
    }

    private void writeKeyPair(String kid) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        writePem(keyDirectory.resolve(kid + ".public.pem"), "PUBLIC KEY", pair.getPublic().getEncoded());
        Path privateKey = keyDirectory.resolve(kid + ".private.pem");
        writePem(privateKey, "PRIVATE KEY", pair.getPrivate().getEncoded());
        if (Files.getFileAttributeView(privateKey,
                java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(keyDirectory, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
            Files.setPosixFilePermissions(privateKey, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        }
    }

    private static void writePem(Path path, String type, byte[] encoded) throws Exception {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
    }

    private record LoginResult(String accessToken, String refreshToken) {}
}
