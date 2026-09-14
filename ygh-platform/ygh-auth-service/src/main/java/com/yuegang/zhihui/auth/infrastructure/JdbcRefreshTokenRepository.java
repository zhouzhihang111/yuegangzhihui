package com.yuegang.zhihui.auth.infrastructure;

import com.yuegang.zhihui.auth.domain.*;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import javax.sql.DataSource;

public final class JdbcRefreshTokenRepository implements RefreshTokenRepository {
    private final DataSource dataSource;

    public JdbcRefreshTokenRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public void insertInitial(long accountId, String family, NewRefreshToken token) {
        try (Connection connection = dataSource.getConnection()) {
            insert(connection, accountId, family, null, token);
        } catch (SQLException failure) {
            throw new AccountSecurityPersistenceException("initial refresh token cannot be stored", failure);
        }
    }

    @Override
    public RefreshRotationResult rotate(String presentedHash, NewRefreshToken replacement, Instant now) {
        Objects.requireNonNull(presentedHash, "presentedHash must not be null");
        Objects.requireNonNull(replacement, "replacement must not be null");
        Objects.requireNonNull(now, "now must not be null");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CurrentToken current = lockByHash(connection, presentedHash);
                if (current == null) {
                    connection.commit();
                    return RefreshRotationResult.invalid();
                }
                if (current.revokedAt != null || current.replacedById != null) {
                    revokeFamily(connection, current.accountId, current.family, now, "REPLAY_DETECTED");
                    connection.commit();
                    return RefreshRotationResult.replay();
                }
                if (!current.expiresAt.isAfter(now)) {
                    revokeOne(connection, current.id, now, "EXPIRED");
                    connection.commit();
                    return RefreshRotationResult.invalid();
                }
                insert(connection, current.accountId, current.family, current.id, replacement);
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE auth_refresh_token SET replaced_by_token_id = ?, last_used_at = ?,
                          revoked_at = ?, revoke_reason = 'ROTATED'
                        WHERE id = ? AND revoked_at IS NULL AND replaced_by_token_id IS NULL
                        """)) {
                    update.setLong(1, replacement.id());
                    setInstant(update, 2, now);
                    setInstant(update, 3, now);
                    update.setLong(4, current.id);
                    if (update.executeUpdate() != 1) throw new SQLException("refresh rotation lost locked row");
                }
                connection.commit();
                return new RefreshRotationResult(RefreshRotationStatus.ROTATED, current.accountId);
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) {
            throw new AccountSecurityPersistenceException("refresh token rotation failed", failure);
        }
    }

    @Override
    public void revokeFamilyByTokenHash(String presentedHash, Instant now, String reason) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CurrentToken current = lockByHash(connection, presentedHash);
                if (current != null) revokeFamily(connection, current.accountId, current.family, now, reason);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) {
            throw new AccountSecurityPersistenceException("refresh token family revocation failed", failure);
        }
    }

    private CurrentToken lockByHash(Connection connection, String hash) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT id, account_id, token_family, expires_at, revoked_at, replaced_by_token_id
                FROM auth_refresh_token WHERE token_hash = ? FOR UPDATE
                """)) {
            query.setString(1, hash);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return null;
                Timestamp revoked = result.getTimestamp("revoked_at", utcCalendar());
                long replaced = result.getLong("replaced_by_token_id");
                boolean replacedIsNull = result.wasNull();
                return new CurrentToken(result.getLong("id"), result.getLong("account_id"),
                        result.getString("token_family"), result.getTimestamp("expires_at", utcCalendar()).toInstant(),
                        revoked == null ? null : revoked.toInstant(), replacedIsNull ? null : replaced);
            }
        }
    }

    private void insert(Connection connection, long accountId, String family, Long parentId, NewRefreshToken token)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO auth_refresh_token
                  (id, account_id, token_hash, token_family, parent_token_id, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setLong(1, token.id());
            insert.setLong(2, accountId);
            insert.setString(3, token.tokenHash());
            insert.setString(4, family);
            if (parentId == null) insert.setNull(5, Types.BIGINT); else insert.setLong(5, parentId);
            setInstant(insert, 6, token.issuedAt());
            setInstant(insert, 7, token.expiresAt());
            insert.executeUpdate();
        }
    }

    private void revokeFamily(Connection connection, long accountId, String family, Instant now, String reason) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE auth_refresh_token SET revoked_at = COALESCE(revoked_at, ?),
                  revoke_reason = CASE WHEN revoke_reason IS NULL OR revoke_reason = 'ROTATED' THEN ? ELSE revoke_reason END
                WHERE account_id = ? AND token_family = ?
                """)) {
            setInstant(update, 1, now);
            update.setString(2, reason);
            update.setLong(3, accountId);
            update.setString(4, family);
            update.executeUpdate();
        }
    }

    private void revokeOne(Connection connection, long id, Instant now, String reason) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE auth_refresh_token SET revoked_at = ?, revoke_reason = ? WHERE id = ?")) {
            setInstant(update, 1, now);
            update.setString(2, reason);
            update.setLong(3, id);
            update.executeUpdate();
        }
    }

    private void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setTimestamp(index, Timestamp.from(value), utcCalendar());
    }

    private java.util.Calendar utcCalendar() {
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
    }

    private void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); } catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }

    private record CurrentToken(long id, long accountId, String family, Instant expiresAt,
                                Instant revokedAt, Long replacedById) {}
}
