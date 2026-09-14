package com.yuegang.zhihui.auth.infrastructure;

import com.yuegang.zhihui.auth.domain.*;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcAccountSecurityRepository implements AccountSecurityRepository {
    private static final String FIND_SQL = """
            SELECT id, status, failed_login_count, locked_until, version
            FROM auth_account WHERE id = ?
            """;
    private static final String UPDATE_SQL = """
            UPDATE auth_account
            SET failed_login_count = ?, locked_until = ?, version = version + 1
            WHERE id = ? AND version = ?
            """;

    private final DataSource dataSource;

    public JdbcAccountSecurityRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<AccountSecuritySnapshot> findById(long accountId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_SQL)) {
            statement.setLong(1, accountId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                Timestamp lockedUntil = rows.getTimestamp("locked_until");
                var state = new AccountAccessState(
                        AccountStatus.valueOf(rows.getString("status")),
                        rows.getInt("failed_login_count"),
                        Optional.ofNullable(lockedUntil).map(Timestamp::toInstant));
                return Optional.of(new AccountSecuritySnapshot(
                        rows.getLong("id"), state, rows.getLong("version")));
            }
        } catch (SQLException exception) {
            throw new AccountSecurityPersistenceException("failed to read account security state", exception);
        }
    }

    @Override
    public boolean compareAndSetAccessState(
            long accountId, long expectedVersion, AccountAccessState newState) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(UPDATE_SQL)) {
            statement.setInt(1, newState.failedLoginCount());
            if (newState.lockedUntil().isPresent()) {
                statement.setTimestamp(2, Timestamp.from(newState.lockedUntil().orElseThrow()));
            } else {
                statement.setNull(2, java.sql.Types.TIMESTAMP);
            }
            statement.setLong(3, accountId);
            statement.setLong(4, expectedVersion);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new AccountSecurityPersistenceException("failed to update account security state", exception);
        }
    }
}
