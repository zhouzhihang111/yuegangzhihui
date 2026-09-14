package com.yuegang.zhihui.user;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;

class UserModuleContractTest {
    @Test void isRunnableBootServiceWithFailFastPrivateDatabaseConfiguration() throws Exception {
        assertThat(UserApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
        String yaml = new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml).contains("name: ygh-user-service", "url: ${YGH_USER_DB_URL}",
                "password: ${YGH_USER_DB_APP_PASSWORD}", "password: ${YGH_USER_DB_MIGRATION_PASSWORD}",
                "locations: classpath:db/migration", "clean-disabled: true");
    }
}
