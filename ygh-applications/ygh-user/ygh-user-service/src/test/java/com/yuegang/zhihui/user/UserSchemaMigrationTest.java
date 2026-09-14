package com.yuegang.zhihui.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashSet;
import java.util.Base64;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.yuegang.zhihui.user.infrastructure.JdbcUserProfileRepository;
import com.yuegang.zhihui.user.infrastructure.AddressCipher;
import com.yuegang.zhihui.user.api.UpdateUserProfileRequest;

class UserSchemaMigrationTest {
    @Test void emptyMysql84MigratesOnceWithOwnershipAndPiiConstraints() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway flyway = Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").cleanDisabled(true)
                    .baselineOnMigrate(false).outOfOrder(false).validateOnMigrate(true).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (Connection connection = DriverManager.getConnection(
                    mysql.jdbcUrl(), mysql.username(), mysql.credential())) {
                assertThat(tables(connection)).contains("user_profile", "user_department", "user_position",
                        "user_employee", "user_employee_position", "user_address", "flyway_schema_history");
                assertThat(columns(connection, "user_address")).contains("recipient_name_ciphertext",
                        "recipient_phone_ciphertext", "address_detail_ciphertext", "pii_key_version", "default_user_id")
                        .doesNotContain("recipient_name", "recipient_phone", "address_detail");
                assertThat(columns(connection, "user_profile")).contains(
                        "phone_ciphertext", "email_ciphertext", "contact_pii_key_version")
                        .doesNotContain("phone", "email");
                assertThat(indexes(connection, "user_address"))
                        .contains("uk_user_address_one_default", "idx_user_address_owner_updated");
                assertThat(importedTables(connection, "user_employee")).contains("user_profile", "user_department");
                assertThat(importedTables(connection, "user_employee_position"))
                        .contains("user_employee", "user_position");
                insertConstraintFixtures(connection);
            }
            var repository = new JdbcUserProfileRepository(new DriverManagerDataSource(
                    mysql.jdbcUrl(), mysql.username(), mysql.credential()),
                    new AddressCipher(Base64.getEncoder().encodeToString(new byte[32]), 1));
            var initial = new UpdateUserProfileRequest("Bob", null, "zh-CN", "Asia/Shanghai", 0);
            assertThat(repository.save(2, initial)).get().extracting(profile -> profile.version()).isEqualTo(0L);
            var changed = new UpdateUserProfileRequest("Bob Chen", "https://cdn.example/bob.png",
                    "+86 13800138000", "BOB@example.com", "en-US", "UTC", 0);
            assertThat(repository.save(2, changed)).get().extracting(profile -> profile.version()).isEqualTo(1L);
            assertThat(repository.save(2, initial)).isEmpty();
            assertThat(repository.findByUserId(2)).get().satisfies(profile -> {
                assertThat(profile.displayName()).isEqualTo("Bob Chen");
                assertThat(profile.phone()).isEqualTo("+86 13800138000");
                assertThat(profile.email()).isEqualTo("bob@example.com");
                assertThat(profile.toString()).doesNotContain("13800138000", "bob@example.com");
            });
        }
    }

    private static void insertConstraintFixtures(Connection connection) throws Exception {
        connection.createStatement().executeUpdate("INSERT INTO user_profile(user_id,display_name) VALUES (1,'Alice')");
        connection.createStatement().executeUpdate(addressInsert(10, "01", "02", "03", true));
        assertThatThrownBy(() -> connection.createStatement().executeUpdate(
                addressInsert(11, "04", "05", "06", true))).isInstanceOf(java.sql.SQLException.class);
        connection.createStatement().executeUpdate(addressInsert(12, "07", "08", "09", false));
    }

    private static String addressInsert(long id, String name, String phone, String detail, boolean isDefault) {
        return """
                INSERT INTO user_address(id,user_id,recipient_name_ciphertext,recipient_phone_ciphertext,
                    pii_key_version,country_code,province_name,city_name,district_name,address_detail_ciphertext,is_default)
                VALUES (%d,1,X'%s',X'%s',1,'CN','广东省','广州市','天河区',X'%s',%s)
                """.formatted(id, name, phone, detail, isDefault ? "TRUE" : "FALSE");
    }

    private static LinkedHashSet<String> tables(Connection c) throws Exception {
        var values = new LinkedHashSet<String>();
        try (var rows = c.getMetaData().getTables(c.getCatalog(), null, null, new String[]{"TABLE"})) {
            while (rows.next()) values.add(rows.getString("TABLE_NAME"));
        }
        return values;
    }
    private static LinkedHashSet<String> columns(Connection c, String table) throws Exception {
        var values = new LinkedHashSet<String>();
        try (var rows = c.getMetaData().getColumns(c.getCatalog(), null, table, null)) {
            while (rows.next()) values.add(rows.getString("COLUMN_NAME"));
        }
        return values;
    }
    private static LinkedHashSet<String> indexes(Connection c, String table) throws Exception {
        var values = new LinkedHashSet<String>();
        try (var rows = c.getMetaData().getIndexInfo(c.getCatalog(), null, table, false, false)) {
            while (rows.next()) if (rows.getString("INDEX_NAME") != null) values.add(rows.getString("INDEX_NAME"));
        }
        return values;
    }
    private static LinkedHashSet<String> importedTables(Connection c, String table) throws Exception {
        var values = new LinkedHashSet<String>();
        try (var rows = c.getMetaData().getImportedKeys(c.getCatalog(), null, table)) {
            while (rows.next()) values.add(rows.getString("PKTABLE_NAME"));
        }
        return values;
    }
}
