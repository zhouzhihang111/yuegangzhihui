package com.yuegang.zhihui.user.infrastructure;

import com.yuegang.zhihui.user.api.*;
import com.yuegang.zhihui.user.domain.AddressRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcAddressRepository implements AddressRepository {
    private static final String SELECT = """
        SELECT id,user_id,label,recipient_name_ciphertext,recipient_phone_ciphertext,pii_key_version,
          country_code,province_code,province_name,city_name,district_name,address_detail_ciphertext,
          postal_code,is_default,version,updated_at FROM user_address
        """;
    private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final AddressCipher cipher;
    public JdbcAddressRepository(DataSource dataSource, AddressCipher cipher) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.cipher = Objects.requireNonNull(cipher);
    }
    @Override public List<AddressView> findAll(long userId) {
        return jdbc.query(SELECT + " WHERE user_id=? ORDER BY is_default DESC,updated_at DESC,id DESC", this::map, userId);
    }
    @Override public AddressView create(long id, long userId, CreateAddressRequest r) {
        return Objects.requireNonNull(tx.execute(status -> {
            boolean makeDefault = r.defaultAddress() || count(userId) == 0;
            if (makeDefault) clearDefault(userId);
            jdbc.update("""
                INSERT INTO user_address(id,user_id,label,recipient_name_ciphertext,recipient_phone_ciphertext,
                  pii_key_version,country_code,province_code,province_name,city_name,district_name,
                  address_detail_ciphertext,postal_code,is_default)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id,userId,blankToNull(r.label()),cipher.encrypt(userId,"recipientName",r.recipientName()),
                    cipher.encrypt(userId,"recipientPhone",r.recipientPhone()),cipher.keyVersion(),r.countryCode(),
                    blankToNull(r.provinceCode()),r.provinceName(),r.cityName(),r.districtName(),
                    cipher.encrypt(userId,"addressDetail",r.addressDetail()),blankToNull(r.postalCode()),makeDefault);
            return one(id,userId).orElseThrow();
        }));
    }
    @Override public Optional<AddressView> update(long id, long userId, UpdateAddressRequest r) {
        return tx.execute(status -> {
            Boolean currentlyDefault = lockedDefault(id,userId,r.version());
            if (currentlyDefault == null) return Optional.empty();
            if (r.defaultAddress() && !currentlyDefault) clearDefault(userId);
            boolean desiredDefault = r.defaultAddress() || (currentlyDefault && count(userId) == 1);
            int changed = jdbc.update("""
                UPDATE user_address SET label=?,recipient_name_ciphertext=?,recipient_phone_ciphertext=?,
                  pii_key_version=?,country_code=?,province_code=?,province_name=?,city_name=?,district_name=?,
                  address_detail_ciphertext=?,postal_code=?,is_default=?,version=version+1
                WHERE id=? AND user_id=? AND version=?
                """,blankToNull(r.label()),cipher.encrypt(userId,"recipientName",r.recipientName()),
                    cipher.encrypt(userId,"recipientPhone",r.recipientPhone()),cipher.keyVersion(),r.countryCode(),
                    blankToNull(r.provinceCode()),r.provinceName(),r.cityName(),r.districtName(),
                    cipher.encrypt(userId,"addressDetail",r.addressDetail()),blankToNull(r.postalCode()),
                    desiredDefault,id,userId,r.version());
            if (changed == 1 && currentlyDefault && !desiredDefault) promoteDefault(userId, id);
            return changed == 1 ? one(id,userId) : Optional.empty();
        });
    }
    @Override public boolean delete(long id, long userId, long version) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            Boolean wasDefault = jdbc.query("SELECT is_default FROM user_address WHERE id=? AND user_id=? AND version=? FOR UPDATE",
                    rs -> rs.next() ? rs.getBoolean(1) : null,id,userId,version);
            if (wasDefault == null || jdbc.update("DELETE FROM user_address WHERE id=? AND user_id=? AND version=?",id,userId,version) != 1) return false;
            if (wasDefault) promoteDefault(userId, id);
            return true;
        }));
    }
    @Override public Optional<AddressView> makeDefault(long id,long userId,long version) {
        return tx.execute(status -> {
            Boolean currentlyDefault = lockedDefault(id,userId,version);
            if (currentlyDefault == null) return Optional.empty();
            if (!currentlyDefault) clearDefault(userId);
            if (jdbc.update("UPDATE user_address SET is_default=TRUE,version=version+1 WHERE id=? AND user_id=? AND version=?",id,userId,version) != 1)
                return Optional.empty();
            return one(id,userId);
        });
    }
    private void clearDefault(long userId) { jdbc.update("UPDATE user_address SET is_default=FALSE,version=version+1 WHERE user_id=? AND is_default=TRUE",userId); }
    private void promoteDefault(long userId,long excludedId) { jdbc.update("""
        UPDATE user_address SET is_default=TRUE,version=version+1 WHERE id=(
          SELECT id FROM (SELECT id FROM user_address WHERE user_id=? AND id<>? ORDER BY updated_at DESC,id DESC LIMIT 1) candidate)
        """,userId,excludedId); }
    private int count(long userId) { return Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM user_address WHERE user_id=?",Integer.class,userId)).orElse(0); }
    private Boolean lockedDefault(long id,long userId,long version) { return jdbc.query("SELECT is_default FROM user_address WHERE id=? AND user_id=? AND version=? FOR UPDATE",rs -> rs.next()?rs.getBoolean(1):null,id,userId,version); }
    private Optional<AddressView> one(long id,long userId) { return jdbc.query(SELECT+" WHERE id=? AND user_id=?",rs -> rs.next()?Optional.of(map(rs,1)):Optional.empty(),id,userId); }
    private AddressView map(ResultSet rs,int row) throws SQLException {
        long owner=rs.getLong("user_id"); int keyVersion=rs.getInt("pii_key_version");
        return new AddressView(Long.toString(rs.getLong("id")),rs.getString("label"),
          cipher.decrypt(owner,"recipientName",keyVersion,rs.getBytes("recipient_name_ciphertext")),
          cipher.decrypt(owner,"recipientPhone",keyVersion,rs.getBytes("recipient_phone_ciphertext")),
          rs.getString("country_code"),rs.getString("province_code"),rs.getString("province_name"),
          rs.getString("city_name"),rs.getString("district_name"),
          cipher.decrypt(owner,"addressDetail",keyVersion,rs.getBytes("address_detail_ciphertext")),
          rs.getString("postal_code"),rs.getBoolean("is_default"),rs.getLong("version"),
          rs.getTimestamp("updated_at").toLocalDateTime().atOffset(ZoneOffset.UTC));
    }
    private static String blankToNull(String value) { return value == null || value.isBlank()?null:value.trim(); }
}
