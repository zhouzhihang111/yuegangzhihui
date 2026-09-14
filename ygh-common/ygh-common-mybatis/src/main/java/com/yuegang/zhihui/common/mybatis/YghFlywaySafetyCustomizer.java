package com.yuegang.zhihui.common.mybatis;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;

/** Enforces forward-only, fail-fast Flyway settings for every business service. */
public final class YghFlywaySafetyCustomizer implements FlywayConfigurationCustomizer {

    @Override
    public void customize(FluentConfiguration configuration) {
        configuration
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .outOfOrder(false)
                .baselineOnMigrate(false)
                .ignoreMigrationPatterns(new String[0]);
    }
}
