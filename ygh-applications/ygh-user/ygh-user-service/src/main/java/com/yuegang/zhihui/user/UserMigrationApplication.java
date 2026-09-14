package com.yuegang.zhihui.user;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Bounded Flyway job; production runtime starts with Flyway disabled. */
public final class UserMigrationApplication {
    private UserMigrationApplication() { }
    public static void main(String[] args) {
        try (var ignored = new SpringApplicationBuilder(UserApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.cloud.nacos.discovery.enabled=false")
                .run(args)) { }
    }
}
