package com.yuegang.zhihui.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.auth.domain.LoginAttempt;
import com.yuegang.zhihui.auth.domain.LoginAttemptResult;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcLoginAttemptRepositoryIntegrationTest {

    @Test
    void persistsSuccessfulAndUnknownAccountAttemptsWithoutRawPrincipalOrTextIp() throws Exception {
        try (var fixture = YghTestContainerFactory.mysql().start()) {
            Flyway.configure()
                    .dataSource(fixture.jdbcUrl(), fixture.username(), fixture.credential())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            try (var connection = DriverManager.getConnection(
                    fixture.jdbcUrl(), fixture.username(), fixture.credential());
                    var insert = connection.prepareStatement("""
                            INSERT INTO auth_account
                              (id, user_id, principal, account_type, status)
                            VALUES (42, 84, 'stored-account', 'CUSTOMER', 'ACTIVE')
                            """)) {
                insert.executeUpdate();
            }

            var ids = new AtomicLong(100);
            var repository = new JdbcLoginAttemptRepository(
                    new DriverManagerDataSource(fixture.jdbcUrl(), fixture.username(), fixture.credential()),
                    ids::getAndIncrement);
            repository.save(new LoginAttempt(
                    42L, "a".repeat(64), "c".repeat(64), LoginAttemptResult.SUCCESS, null,
                    Instant.parse("2026-07-12T00:00:00Z"), "trace-success"));
            repository.save(new LoginAttempt(
                    null, "b".repeat(64), "d".repeat(64),
                    LoginAttemptResult.INVALID_CREDENTIALS, "ACCOUNT_NOT_FOUND",
                    Instant.parse("2026-07-12T00:00:01Z"), "trace-failure"));

            try (var connection = DriverManager.getConnection(
                    fixture.jdbcUrl(), fixture.username(), fixture.credential());
                    var rows = connection.createStatement().executeQuery("""
                            SELECT id, account_id, principal_hash, client_ip_hash,
                                   result, failure_reason, trace_id
                            FROM auth_login_attempt ORDER BY id
                            """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("account_id")).isEqualTo(42);
                assertThat(rows.getString("principal_hash")).isEqualTo("a".repeat(64));
                assertThat(rows.getString("client_ip_hash")).isEqualTo("c".repeat(64));
                assertThat(rows.getString("result")).isEqualTo("SUCCESS");
                assertThat(rows.getString("failure_reason")).isNull();
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("account_id")).isNull();
                assertThat(rows.getString("principal_hash")).isEqualTo("b".repeat(64));
                assertThat(rows.getString("client_ip_hash")).isEqualTo("d".repeat(64));
                assertThat(rows.getString("failure_reason")).isEqualTo("ACCOUNT_NOT_FOUND");
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
