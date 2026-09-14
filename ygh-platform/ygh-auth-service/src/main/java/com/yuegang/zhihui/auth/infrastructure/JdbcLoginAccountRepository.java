package com.yuegang.zhihui.auth.infrastructure;

import com.yuegang.zhihui.auth.domain.*;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcLoginAccountRepository implements LoginAccountRepository {
    private static final String FIND_SQL = """
            SELECT a.id, a.user_id, a.account_type, a.status,
                   c.password_hash, c.password_algorithm, c.password_version
            FROM auth_account a
            JOIN auth_credential c ON c.account_id = a.id
            WHERE a.principal = ?
            """;
    private static final String FIND_BY_ID_SQL = """
            SELECT a.id, a.user_id, a.account_type, a.status,
                   c.password_hash, c.password_algorithm, c.password_version
            FROM auth_account a JOIN auth_credential c ON c.account_id = a.id
            WHERE a.id = ?
            """;
    private final DataSource dataSource;

    public JdbcLoginAccountRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<LoginAccount> findByPrincipal(String normalizedPrincipal) {
        String principal = PrincipalNormalizer.normalize(normalizedPrincipal);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_SQL)) {
            statement.setString(1, principal);
            try (var rows = statement.executeQuery()) { return read(rows); }
        } catch (SQLException | IllegalArgumentException failure) {
            throw new AccountSecurityPersistenceException("login account cannot be read", failure);
        }
    }

    @Override
    public Optional<LoginAccount> findByAccountId(long accountId) {
        if (accountId <= 0) throw new IllegalArgumentException("accountId must be positive");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
            statement.setLong(1, accountId);
            try (var rows = statement.executeQuery()) { return read(rows); }
        } catch (SQLException | IllegalArgumentException failure) {
            throw new AccountSecurityPersistenceException("login account cannot be read", failure);
        }
    }

    @Override
    public LoginAccount create(long accountId, long userId, String normalizedPrincipal,
            String accountType, PasswordDigest passwordDigest) {
        String principal = PrincipalNormalizer.normalize(normalizedPrincipal);
        var account = new LoginAccount(accountId, userId, accountType, AccountStatus.ACTIVE, passwordDigest);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var accountStatement = connection.prepareStatement("""
                    INSERT INTO auth_account
                    (id,user_id,principal,account_type,status,failed_login_count,version)
                    VALUES (?,?,?,?, 'ACTIVE',0,0)
                    """);
                    var credentialStatement = connection.prepareStatement("""
                    INSERT INTO auth_credential
                    (id,account_id,password_hash,password_algorithm,password_version,changed_at)
                    VALUES (?,?,?,?,?,CURRENT_TIMESTAMP(6))
                    """)) {
                accountStatement.setLong(1, accountId);
                accountStatement.setLong(2, userId);
                accountStatement.setString(3, principal);
                accountStatement.setString(4, accountType);
                accountStatement.executeUpdate();
                credentialStatement.setLong(1, accountId);
                credentialStatement.setLong(2, accountId);
                credentialStatement.setString(3, passwordDigest.hash());
                credentialStatement.setString(4, passwordDigest.algorithm());
                credentialStatement.setInt(5, passwordDigest.version());
                credentialStatement.executeUpdate();
                connection.commit();
                return account;
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) {
            if ("23000".equals(failure.getSQLState())) throw new AccountAlreadyExistsException(failure);
            throw new AccountSecurityPersistenceException("login account cannot be created", failure);
        }
    }

    private static Optional<LoginAccount> read(java.sql.ResultSet rows) throws SQLException {
        if (!rows.next()) return Optional.empty();
        return Optional.of(new LoginAccount(
                rows.getLong("id"), rows.getLong("user_id"), rows.getString("account_type"),
                AccountStatus.valueOf(rows.getString("status")),
                new PasswordDigest(rows.getString("password_hash"),
                        rows.getString("password_algorithm"), rows.getInt("password_version"))));
    }
}
