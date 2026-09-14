package com.yuegang.zhihui.user.infrastructure;

import com.yuegang.zhihui.user.api.*;
import com.yuegang.zhihui.user.domain.UserProfileRepository;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

public final class JdbcUserProfileRepository implements UserProfileRepository {
    private final DataSource dataSource;
    private final AddressCipher cipher;
    public JdbcUserProfileRepository(DataSource dataSource, AddressCipher cipher) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.cipher = Objects.requireNonNull(cipher);
    }
    public Optional<UserProfileView> findByUserId(long userId) {
        try (var c = dataSource.getConnection(); var s = c.prepareStatement(
                "SELECT user_id,display_name,avatar_url,phone_ciphertext,email_ciphertext,contact_pii_key_version,locale,timezone,version FROM user_profile WHERE user_id=?")) {
            s.setLong(1, userId);
            try (var rows = s.executeQuery()) { return read(rows); }
        } catch (SQLException failure) { throw new IllegalStateException("user profile cannot be read", failure); }
    }
    public Optional<UserProfileView> save(long userId, UpdateUserProfileRequest request) {
        try (var c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Long current = lockVersion(c, userId);
                if (current == null) {
                    if (request.version() != 0) { c.rollback(); return Optional.empty(); }
                    try (var s = c.prepareStatement("""
                            INSERT IGNORE INTO user_profile(user_id,display_name,avatar_url,phone_ciphertext,email_ciphertext,
                                contact_pii_key_version,locale,timezone,profile_completed,version)
                            VALUES (?,?,?,?,?,?,?,?,TRUE,0)
                            """)) { bindInsert(s, userId, request); s.executeUpdate(); }
                } else {
                    if (current != request.version()) { c.rollback(); return Optional.empty(); }
                    try (var s = c.prepareStatement("""
                            UPDATE user_profile SET display_name=?,avatar_url=?,phone_ciphertext=?,email_ciphertext=?,
                                contact_pii_key_version=?,locale=?,timezone=?,profile_completed=TRUE,version=version+1
                            WHERE user_id=? AND version=?
                            """)) {
                        s.setString(1, request.displayName().trim()); s.setString(2, blankToNull(request.avatarUrl()));
                        setEncrypted(s, 3, userId, "profilePhone", request.phone());
                        setEncrypted(s, 4, userId, "profileEmail", normalizedEmail(request.email()));
                        setKeyVersion(s, 5, request.phone(), request.email());
                        s.setString(6, request.locale()); s.setString(7, request.timezone());
                        s.setLong(8, userId); s.setLong(9, current);
                        if (s.executeUpdate() != 1) { c.rollback(); return Optional.empty(); }
                    }
                }
                c.commit();
            } catch (SQLException failure) { c.rollback(); throw failure; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException failure) { throw new IllegalStateException("user profile cannot be saved", failure); }
        return findByUserId(userId);
    }
    private static Long lockVersion(Connection c, long userId) throws SQLException {
        try (var s = c.prepareStatement("SELECT version FROM user_profile WHERE user_id=? FOR UPDATE")) {
            s.setLong(1, userId); try (var rows = s.executeQuery()) { return rows.next() ? rows.getLong(1) : null; }
        }
    }
    private void bindInsert(PreparedStatement s, long userId, UpdateUserProfileRequest r) throws SQLException {
        s.setLong(1, userId); s.setString(2, r.displayName().trim()); s.setString(3, blankToNull(r.avatarUrl()));
        setEncrypted(s, 4, userId, "profilePhone", r.phone());
        setEncrypted(s, 5, userId, "profileEmail", normalizedEmail(r.email()));
        setKeyVersion(s, 6, r.phone(), r.email());
        s.setString(7, r.locale()); s.setString(8, r.timezone());
    }
    private Optional<UserProfileView> read(ResultSet rows) throws SQLException {
        if (!rows.next()) return Optional.empty();
        long userId = rows.getLong("user_id");
        byte[] phone = rows.getBytes("phone_ciphertext");
        byte[] email = rows.getBytes("email_ciphertext");
        int keyVersion = rows.getInt("contact_pii_key_version");
        return Optional.of(new UserProfileView(Long.toString(userId), rows.getString("display_name"),
                rows.getString("avatar_url"), decrypt(userId, "profilePhone", keyVersion, phone),
                decrypt(userId, "profileEmail", keyVersion, email), rows.getString("locale"),
                rows.getString("timezone"), rows.getLong("version")));
    }
    private void setEncrypted(PreparedStatement statement, int index, long userId, String field, String value) throws SQLException {
        String normalized = blankToNull(value);
        if (normalized == null) { statement.setNull(index, Types.VARBINARY); return; }
        statement.setBytes(index, cipher.encrypt(userId, field, normalized));
    }
    private void setKeyVersion(PreparedStatement statement, int index, String phone, String email) throws SQLException {
        if (blankToNull(phone) == null && blankToNull(email) == null) statement.setNull(index, Types.SMALLINT);
        else statement.setInt(index, cipher.keyVersion());
    }
    private String decrypt(long userId, String field, int keyVersion, byte[] value) {
        return value == null ? null : cipher.decrypt(userId, field, keyVersion, value);
    }
    private static String normalizedEmail(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
