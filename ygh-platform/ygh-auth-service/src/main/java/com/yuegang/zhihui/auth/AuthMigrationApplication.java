package com.yuegang.zhihui.auth;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Runs the validated Flyway lifecycle and exits without opening an HTTP port. */
public final class AuthMigrationApplication {

    private AuthMigrationApplication() {
    }

    public static void main(String[] args) {
        try (var ignored = new SpringApplicationBuilder(AuthApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.cloud.nacos.discovery.enabled=false")
                .run(args)) {
            // Closing the context makes this a bounded deployment migration job.
        }
    }
}
