package com.yuegang.zhihui.auth.application;

import static org.assertj.core.api.Assertions.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AuthAccountQueryServiceTest {
    @Test void filtersMasksValidatesAndBoundsAdministrativeAccountQueries() {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO auth_account(id,user_id,principal,account_type,status) VALUES(1,101,'buyer@example.com','EMAIL','ACTIVE'),(2,202,'13800138000','PHONE','DISABLED')");
            var service = new AuthAccountQueryService(dataSource);

            assertThat(service.list(null, null, 500)).hasSize(2);
            assertThat(service.list("buyer", "active", 10)).singleElement().satisfies(account -> {
                assertThat(account.userId()).isEqualTo("101");
                assertThat(account.principal()).isEqualTo("b***@example.com");
            });
            assertThat(service.list("202", "DISABLED", 10)).singleElement()
                    .extracting(account -> account.principal()).isEqualTo("138****8000");
            assertThat(service.list("missing", null, 0)).isEmpty();
            assertThatThrownBy(() -> service.list(null, "DELETED", 10)).isInstanceOf(BusinessException.class);
        }
    }
}
