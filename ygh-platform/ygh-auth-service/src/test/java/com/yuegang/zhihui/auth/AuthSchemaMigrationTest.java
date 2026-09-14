package com.yuegang.zhihui.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.common.test.JdbcContainerFixture;
import com.yuegang.zhihui.auth.application.AccountLockService;
import com.yuegang.zhihui.auth.domain.AccountLockPolicy;
import com.yuegang.zhihui.auth.domain.Argon2PasswordHasher;
import com.yuegang.zhihui.auth.infrastructure.JdbcAccountSecurityRepository;
import java.sql.DriverManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

class AuthSchemaMigrationTest {

    @Test
    void emptyMysql84DatabaseMigratesAndValidatesIdempotently() throws Exception {
        try (var fixture = YghTestContainerFactory.mysql().start()) {
            String migrationUser = "auth_migration_test";
            String migrationCredential = testCredential();
            String appUser = "auth_app_test";
            String appCredential = testCredential();
            provisionSeparatedUsers(fixture, migrationUser, migrationCredential, appUser, appCredential);
            Flyway flyway = Flyway.configure()
                    .dataSource(fixture.jdbcUrl(), migrationUser, migrationCredential)
                    .locations("classpath:db/migration")
                    .cleanDisabled(true)
                    .baselineOnMigrate(false)
                    .outOfOrder(false)
                    .validateOnMigrate(true)
                    .load();

            var first = flyway.migrate();
            flyway.validate();
            var second = flyway.migrate();
            grantAppTablePrivileges(fixture, appUser);

            assertThat(first.migrationsExecuted).isEqualTo(4);
            assertThat(second.migrationsExecuted).isZero();
            try (var connection = DriverManager.getConnection(
                    fixture.jdbcUrl(), fixture.username(), fixture.credential())) {
                assertThat(tableNames(connection)).contains(
                        "auth_account",
                        "auth_credential",
                        "auth_refresh_token",
                        "auth_login_attempt", "auth_account_admin_audit",
                        "flyway_schema_history");
                assertThat(columnNames(connection, "auth_account")).contains(
                        "id", "user_id", "principal", "account_type", "status",
                        "failed_login_count", "locked_until", "last_login_at", "version",
                        "created_at", "updated_at");
                assertThat(columnNames(connection, "auth_credential")).contains(
                        "account_id", "password_hash", "password_algorithm",
                        "password_version", "changed_at");
                assertThat(columnNames(connection, "auth_refresh_token")).contains(
                        "account_id", "token_hash", "token_family", "expires_at",
                        "revoked_at", "replaced_by_token_id");
                assertThat(columnNames(connection, "auth_login_attempt")).contains(
                        "principal_hash", "client_ip_hash", "result", "failure_reason",
                        "occurred_at", "trace_id");
                assertThat(indexNames(connection, "auth_account"))
                        .contains("uk_auth_account_principal", "idx_auth_account_status");
                assertThat(indexNames(connection, "auth_refresh_token"))
                        .contains("uk_auth_refresh_token_hash", "idx_auth_refresh_account_family_expiry",
                                "idx_auth_refresh_expiry");
                assertThat(indexNames(connection, "auth_login_attempt"))
                        .contains("idx_auth_login_occurred_at", "idx_auth_login_ip_hash_time");
                assertThat(indexNames(connection, "auth_account_admin_audit"))
                        .contains("idx_auth_admin_audit_account_created", "idx_auth_admin_audit_operator_created");
                assertThat(importedKeyTables(connection, "auth_credential"))
                        .contains("auth_account");
            }

            try (var context = new SpringApplicationBuilder(AuthApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties(
                            "YGH_AUTH_DB_URL=" + fixture.jdbcUrl(),
                            "YGH_AUTH_DB_APP_USERNAME=" + appUser,
                            "YGH_AUTH_DB_APP_PASSWORD=" + appCredential,
                            "YGH_AUTH_DB_MIGRATION_USERNAME=" + migrationUser,
                            "YGH_AUTH_DB_MIGRATION_PASSWORD=" + migrationCredential,
                            "spring.cloud.nacos.discovery.enabled=false",
                            "YGH_NACOS_SERVER_ADDR=127.0.0.1:8848",
                            "YGH_NACOS_USERNAME=test",
                            "YGH_NACOS_PASSWORD=test",
                            "YGH_REDIS_HOST=127.0.0.1",
                            "YGH_REDIS_PASSWORD=change-me",
                            "YGH_REDIS_ENVIRONMENT=test",
                            "YGH_AUTH_AUDIT_PEPPER_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                            "YGH_INTERNAL_REQUEST_HMAC_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
                    .run()) {
                assertThat(context.isActive()).isTrue();
            }

            try (var runtimeContext = new SpringApplicationBuilder(AuthApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .properties(
                            "YGH_AUTH_DB_URL=" + fixture.jdbcUrl(),
                            "YGH_AUTH_DB_APP_USERNAME=" + appUser,
                            "YGH_AUTH_DB_APP_PASSWORD=" + appCredential,
                            "spring.flyway.enabled=false",
                            "YGH_AUTH_PORT=0",
                            "spring.cloud.nacos.discovery.enabled=false",
                            "YGH_NACOS_SERVER_ADDR=127.0.0.1:8848",
                            "YGH_NACOS_USERNAME=test",
                            "YGH_NACOS_PASSWORD=test",
                            "YGH_REDIS_HOST=127.0.0.1",
                            "YGH_REDIS_PASSWORD=change-me",
                            "YGH_REDIS_ENVIRONMENT=test",
                            "YGH_AUTH_AUDIT_PEPPER_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                            "YGH_INTERNAL_REQUEST_HMAC_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
                    .run()) {
                assertThat(runtimeContext.isActive()).isTrue();
                assertThat(runtimeContext.containsBean("flyway")).isFalse();
                assertThat(runtimeContext.getBean(
                        com.yuegang.zhihui.common.web.GlobalExceptionHandler.class)).isNotNull();
                int port = ((ServletWebServerApplicationContext) runtimeContext)
                        .getWebServer().getPort();
                assertRuntimeErrorEnvelope(port);
            }

            try (var appConnection = DriverManager.getConnection(
                    fixture.jdbcUrl(), appUser, appCredential)) {
                appConnection.createStatement().executeUpdate("""
                        INSERT INTO auth_account
                        (id, user_id, principal, account_type, status)
                        VALUES (1, 1, 'user@example.test', 'PASSWORD', 'ACTIVE')
                        """);
                char[] rawCharacters = "enterprise passphrase 企业安全口令".toCharArray();
                var digest = Argon2PasswordHasher.owaspMinimum().hash(rawCharacters);
                try (var credential = appConnection.prepareStatement("""
                        INSERT INTO auth_credential
                        (id, account_id, password_hash, password_algorithm, password_version, changed_at)
                        VALUES (1, 1, ?, ?, ?, CURRENT_TIMESTAMP(6))
                        """)) {
                    credential.setString(1, digest.hash());
                    credential.setString(2, digest.algorithm());
                    credential.setInt(3, digest.version());
                    credential.executeUpdate();
                }
                try (var rows = appConnection.createStatement().executeQuery(
                        "SELECT password_hash FROM auth_credential WHERE account_id = 1")) {
                    rows.next();
                    String storedHash = rows.getString(1);
                    assertThat(storedHash).startsWith("$argon2id$")
                            .doesNotContain(new String(rawCharacters));
                }
                assertThat(queryCount(appConnection, "auth_account")).isEqualTo(1);
                assertThatThrownBy(() -> appConnection.createStatement()
                        .execute("CREATE TABLE forbidden_ddl (id BIGINT)"))
                        .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> appConnection.createStatement()
                        .executeUpdate("DELETE FROM flyway_schema_history WHERE 1 = 0"))
                        .isInstanceOf(java.sql.SQLException.class);
            }

            var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    fixture.jdbcUrl(), appUser, appCredential);
            var repository = new JdbcAccountSecurityRepository(dataSource);
            Instant failureTime = Instant.parse("2026-07-12T01:00:00Z");
            var lockService = new AccountLockService(
                    repository,
                    new AccountLockPolicy(5, Duration.ofMinutes(15)),
                    Clock.fixed(failureTime, ZoneOffset.UTC));
            for (int attempt = 0; attempt < 5; attempt++) {
                lockService.recordFailure(1);
            }
            var locked = repository.findById(1).orElseThrow().accessState();
            assertThat(locked.failedLoginCount()).isEqualTo(5);
            assertThat(locked.lockedUntil()).hasValue(failureTime.plus(Duration.ofMinutes(15)));
            assertThat(lockService.authenticationAllowed(1)).isFalse();

            var afterExpiry = new AccountLockService(
                    repository,
                    new AccountLockPolicy(5, Duration.ofMinutes(15)),
                    Clock.fixed(failureTime.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
            assertThat(afterExpiry.recordFailure(1).failedLoginCount()).isEqualTo(1);
            assertThat(afterExpiry.recordSuccess(1).failedLoginCount()).isZero();

            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var attempts = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int attempt = 0; attempt < 10; attempt++) {
                    attempts.add(executor.submit(() -> afterExpiry.recordFailure(1)));
                }
                for (var attempt : attempts) {
                    attempt.get();
                }
            }
            var concurrentlyLocked = repository.findById(1).orElseThrow().accessState();
            assertThat(concurrentlyLocked.failedLoginCount()).isEqualTo(5);
            assertThat(concurrentlyLocked.lockedAt(failureTime.plus(Duration.ofMinutes(16))))
                    .isTrue();

            Instant tokenTime = Instant.parse("2026-07-12T02:00:00Z");
            var refreshTokens = new com.yuegang.zhihui.auth.application.OpaqueRefreshTokenService(
                    new com.yuegang.zhihui.auth.infrastructure.JdbcRefreshTokenRepository(dataSource),
                    Clock.fixed(tokenTime, ZoneOffset.UTC), Duration.ofDays(14));
            var initial = refreshTokens.issueInitial(1);
            char[] initialValue = initial.value().toCharArray();
            var rotation = refreshTokens.rotate(initialValue);
            java.util.Arrays.fill(initialValue, '\0');
            assertThat(rotation.result().status())
                    .isEqualTo(com.yuegang.zhihui.auth.domain.RefreshRotationStatus.ROTATED);
            assertThat(rotation.result().accountId()).isEqualTo(1);
            assertThat(rotation.replacement().value()).isNotEqualTo(initial.value());

            char[] replayedValue = initial.value().toCharArray();
            var replay = refreshTokens.rotate(replayedValue);
            java.util.Arrays.fill(replayedValue, '\0');
            assertThat(replay.result().status())
                    .isEqualTo(com.yuegang.zhihui.auth.domain.RefreshRotationStatus.REPLAY_DETECTED);
            assertThat(replay.replacement()).isNull();
            try (var connection = DriverManager.getConnection(fixture.jdbcUrl(), appUser, appCredential);
                 var rows = connection.createStatement().executeQuery("""
                         SELECT COUNT(*), SUM(revoked_at IS NOT NULL),
                           SUM(revoke_reason = 'REPLAY_DETECTED')
                         FROM auth_refresh_token
                         """)) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.getInt(2)).isEqualTo(2);
                assertThat(rows.getInt(3)).isEqualTo(2);
            }
            try (var connection = DriverManager.getConnection(fixture.jdbcUrl(), appUser, appCredential);
                 var explain = connection.prepareStatement("""
                         EXPLAIN SELECT id FROM auth_refresh_token
                         WHERE account_id = ? AND token_family = ?
                         """)) {
                explain.setLong(1, 1);
                try (var family = connection.createStatement().executeQuery(
                        "SELECT token_family FROM auth_refresh_token LIMIT 1")) {
                    family.next();
                    explain.setString(2, family.getString(1));
                }
                try (var plan = explain.executeQuery()) {
                    plan.next();
                    assertThat(plan.getString("key")).isEqualTo("idx_auth_refresh_account_family_expiry");
                }
            }
        }
    }

    private static void assertRuntimeErrorEnvelope(int port) throws Exception {
        var client = HttpClient.newHttpClient();
        var malformed = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", "trace-malformed")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"principal\":\"user\",\"password\":\"Sensitive-123\""))
                .build();
        var malformedResponse = client.send(malformed, HttpResponse.BodyHandlers.ofString());
        assertThat(malformedResponse.statusCode()).isEqualTo(400);
        String malformedTrace = malformedResponse.headers()
                .firstValue("X-Trace-Id").orElseThrow();
        assertThat(malformedResponse.body()).contains("VALIDATION_ERROR", malformedTrace)
                .doesNotContain("Sensitive-123");

        var unavailable = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Trace-Id", "trace-unavailable")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"principal\":\"user\",\"password\":\"Sensitive-456\"}"))
                .build();
        var unavailableResponse = client.send(unavailable, HttpResponse.BodyHandlers.ofString());
        assertThat(unavailableResponse.statusCode()).isEqualTo(503);
        String unavailableTrace = unavailableResponse.headers()
                .firstValue("X-Trace-Id").orElseThrow();
        assertThat(unavailableResponse.body()).contains("DEPENDENCY_UNAVAILABLE", unavailableTrace)
                .doesNotContain("Sensitive-456");

        var wrongMethod = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .PUT(HttpRequest.BodyPublishers.noBody()).build();
        var wrongMethodResponse = client.send(wrongMethod, HttpResponse.BodyHandlers.ofString());
        assertThat(wrongMethodResponse.statusCode()).isEqualTo(405);
        assertThat(wrongMethodResponse.headers().firstValue("Allow")).hasValue("POST");
        assertThat(wrongMethodResponse.body()).contains("VALIDATION_ERROR");

        var wrongMediaType = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("Sensitive-789"))
                .build();
        var wrongMediaResponse = client.send(wrongMediaType, HttpResponse.BodyHandlers.ofString());
        assertThat(wrongMediaResponse.statusCode()).isEqualTo(415);
        assertThat(wrongMediaResponse.headers().firstValue("Accept").orElse(""))
                .contains("application/json");
        assertThat(wrongMediaResponse.body()).contains("VALIDATION_ERROR")
                .doesNotContain("Sensitive-789");

        var missing = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/v1/auth/not-a-real-resource"))
                .GET().build();
        var missingResponse = client.send(missing, HttpResponse.BodyHandlers.ofString());
        assertThat(missingResponse.statusCode()).isEqualTo(404);
        assertThat(missingResponse.body()).contains("RESOURCE_NOT_FOUND")
                .doesNotContain("not-a-real-resource");
    }

    private static void provisionSeparatedUsers(
            JdbcContainerFixture fixture,
            String migrationUser,
            String migrationCredential,
            String appUser,
            String appCredential
    ) throws Exception {
        try (var connection = DriverManager.getConnection(
                fixture.jdbcUrl(), fixture.adminUsername(), fixture.adminCredential());
                var statement = connection.createStatement()) {
            statement.execute("CREATE USER '" + migrationUser + "'@'%' IDENTIFIED BY '"
                    + migrationCredential + "'");
            statement.execute("CREATE USER '" + appUser + "'@'%' IDENTIFIED BY '"
                    + appCredential + "'");
            String catalog = connection.getCatalog().replace("`", "``");
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, "
                    + "REFERENCES, DROP ON `" + catalog + "`.* TO '" + migrationUser + "'@'%'");
        }
    }

    private static void grantAppTablePrivileges(
            JdbcContainerFixture fixture, String appUser) throws Exception {
        try (var connection = DriverManager.getConnection(
                fixture.jdbcUrl(), fixture.adminUsername(), fixture.adminCredential());
                var statement = connection.createStatement()) {
            String catalog = connection.getCatalog().replace("`", "``");
            for (String table : java.util.List.of(
                    "auth_account", "auth_credential", "auth_refresh_token", "auth_login_attempt",
                    "auth_account_admin_audit")) {
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON `" + catalog
                        + "`.`" + table + "` TO '" + appUser + "'@'%'");
            }
        }
    }

    private static long queryCount(java.sql.Connection connection, String table) throws Exception {
        try (var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static LinkedHashSet<String> tableNames(java.sql.Connection connection) throws Exception {
        var names = new LinkedHashSet<String>();
        try (var rows = connection.getMetaData().getTables(
                connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (rows.next()) {
                names.add(rows.getString("TABLE_NAME").toLowerCase());
            }
        }
        return names;
    }

    private static LinkedHashSet<String> columnNames(
            java.sql.Connection connection, String table) throws Exception {
        var names = new LinkedHashSet<String>();
        try (var rows = connection.getMetaData().getColumns(
                connection.getCatalog(), null, table, "%")) {
            while (rows.next()) {
                names.add(rows.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return names;
    }

    private static LinkedHashSet<String> indexNames(
            java.sql.Connection connection, String table) throws Exception {
        var names = new LinkedHashSet<String>();
        try (var rows = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, table, false, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME");
                if (name != null) {
                    names.add(name.toLowerCase());
                }
            }
        }
        return names;
    }

    private static LinkedHashSet<String> importedKeyTables(
            java.sql.Connection connection, String table) throws Exception {
        var names = new LinkedHashSet<String>();
        try (var rows = connection.getMetaData().getImportedKeys(
                connection.getCatalog(), null, table)) {
            while (rows.next()) {
                names.add(rows.getString("PKTABLE_NAME").toLowerCase());
            }
        }
        return names;
    }

    private static String testCredential() {
        return "T" + java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
