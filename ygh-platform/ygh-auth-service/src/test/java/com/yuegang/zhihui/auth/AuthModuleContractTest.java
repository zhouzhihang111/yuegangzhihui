package com.yuegang.zhihui.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;

class AuthModuleContractTest {

    @Test
    void moduleIsAStandardBootServiceWithFailFastExternalConfiguration() throws Exception {
        assertThat(AuthApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();

        var application = new ClassPathResource("application.yml");
        assertThat(application.exists()).isTrue();
        String yaml = application.getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml)
                .contains("name: ygh-auth-service")
                .contains("url: ${YGH_AUTH_DB_URL}")
                .contains("username: ${YGH_AUTH_DB_APP_USERNAME:ygh_auth_app}")
                .contains("password: ${YGH_AUTH_DB_APP_PASSWORD}")
                .contains("user: ${YGH_AUTH_DB_MIGRATION_USERNAME:ygh_auth_migration}")
                .contains("password: ${YGH_AUTH_DB_MIGRATION_PASSWORD}")
                .contains("server-addr: ${YGH_NACOS_SERVER_ADDR}")
                .contains("audit-pepper-base64: ${YGH_AUTH_AUDIT_PEPPER_BASE64}")
                .contains("clean-disabled: true")
                .contains("baseline-on-migrate: false")
                .contains("out-of-order: false")
                .contains("validate-on-migrate: true")
                .contains("locations: classpath:db/migration");
    }

    @Test
    void initialMigrationUsesTheOnlyAllowedForwardMigrationPath() throws Exception {
        var migration = new ClassPathResource("db/migration/V1__create_auth_schema.sql");
        assertThat(migration.exists()).isTrue();
        String sql = migration.getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql).contains(
                "create table auth_account",
                "create table auth_credential",
                "create table auth_refresh_token",
                "create table auth_login_attempt");
        assertThat(sql).doesNotContain("plain_password", "raw_token", "drop table");
    }

    @Test
    void secondMigrationRemovesReversibleClientIpFromAuditStorage() throws Exception {
        var migration = new ClassPathResource("db/migration/V2__hash_login_audit_client_ip.sql");
        assertThat(migration.exists()).isTrue();
        String sql = migration.getContentAsString(StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql).contains("client_ip_hash", "drop column client_ip", "random_bytes(32)");
        assertThat(sql).doesNotContain("sha2(", "hex(client_ip)");
        assertThat(sql).doesNotContain("drop table", "truncate table");
    }
}
