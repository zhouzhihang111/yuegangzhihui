package com.yuegang.zhihui.auth.infrastructure;

import static org.assertj.core.api.Assertions.*;
import com.yuegang.zhihui.auth.domain.AccountStatus;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcAccountAdministrationRepositoryTest {
    @Test void updatesWithOptimisticLockAndWritesImmutableAudit() throws Exception {
        try(var mysql=YghTestContainerFactory.mysql().start()){
            Flyway.configure().dataSource(mysql.jdbcUrl(),mysql.username(),mysql.credential()).locations("classpath:db/migration").load().migrate();
            try(var c=DriverManager.getConnection(mysql.jdbcUrl(),mysql.username(),mysql.credential())){
                c.createStatement().executeUpdate("INSERT INTO auth_account(id,user_id,principal,account_type,status,version) VALUES(1,20,'u@example.com','USER','ACTIVE',0)");
            }
            var repository=new JdbcAccountAdministrationRepository(new DriverManagerDataSource(mysql.jdbcUrl(),mysql.username(),mysql.credential()));
            var result=repository.changeStatus(20,AccountStatus.DISABLED,0,10,"risk review").orElseThrow();
            assertThat(result.accountId()).isEqualTo(1);assertThat(result.status()).isEqualTo(AccountStatus.DISABLED);assertThat(result.version()).isEqualTo(1);
            assertThat(repository.changeStatus(20,AccountStatus.ACTIVE,0,10,"stale")).isEmpty();
            assertThat(repository.changeStatus(404,AccountStatus.ACTIVE,0,10,null)).isEmpty();
            try(var c=DriverManager.getConnection(mysql.jdbcUrl(),mysql.username(),mysql.credential());var rows=c.createStatement().executeQuery("SELECT action,operator_user_id,reason FROM auth_account_admin_audit")){
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("DISABLED");assertThat(rows.getLong(2)).isEqualTo(10);assertThat(rows.getString(3)).isEqualTo("risk review");assertThat(rows.next()).isFalse();
            }
        }
    }
}
