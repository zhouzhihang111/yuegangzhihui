package com.yuegang.zhihui.auth.infrastructure;

import com.yuegang.zhihui.auth.domain.LoginAttempt;
import com.yuegang.zhihui.auth.domain.LoginAttemptRepository;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.function.LongSupplier;
import javax.sql.DataSource;

public final class JdbcLoginAttemptRepository implements LoginAttemptRepository {
    private static final String INSERT_SQL = """
            INSERT INTO auth_login_attempt
              (id, account_id, principal_hash, client_ip_hash, result, failure_reason, occurred_at, trace_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private final DataSource dataSource;
    private final LongSupplier idSupplier;

    public JdbcLoginAttemptRepository(DataSource dataSource) {
        this(dataSource, positiveRandomIds());
    }

    public JdbcLoginAttemptRepository(DataSource dataSource, LongSupplier idSupplier) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier must not be null");
    }

    @Override
    public void save(LoginAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt must not be null");
        long id = idSupplier.getAsLong();
        if (id <= 0) throw new IllegalStateException("login audit id supplier returned a non-positive value");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setLong(1, id);
            if (attempt.accountId() == null) statement.setNull(2, Types.BIGINT);
            else statement.setLong(2, attempt.accountId());
            statement.setString(3, attempt.principalHash());
            statement.setString(4, attempt.clientIpHash());
            statement.setString(5, attempt.result().name());
            if (attempt.failureReason() == null) statement.setNull(6, Types.VARCHAR);
            else statement.setString(6, attempt.failureReason());
            statement.setTimestamp(7, Timestamp.from(attempt.occurredAt()), utc());
            statement.setString(8, attempt.traceId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("login audit insert did not affect exactly one row");
            }
        } catch (SQLException failure) {
            throw new AccountSecurityPersistenceException("login audit cannot be stored", failure);
        }
    }

    private static LongSupplier positiveRandomIds() {
        SecureRandom random = new SecureRandom();
        return () -> random.nextLong(1, Long.MAX_VALUE);
    }

    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }
}
