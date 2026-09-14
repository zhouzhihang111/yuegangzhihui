package com.yuegang.zhihui.common.mybatis;

import java.util.Objects;
import org.flywaydb.core.Flyway;

/** Delegates checksum and applied-history integrity checks to Flyway's public API. */
public final class FlywayHistoryValidator {

    public void validateOrThrow(Flyway flyway) {
        Objects.requireNonNull(flyway, "flyway must not be null");
        var result = flyway.validateWithResult();
        if (!result.validationSuccessful) {
            throw new MigrationPolicyException(
                    MigrationViolationCode.HISTORY_VALIDATION_FAILED,
                    "Flyway history validation failed with "
                            + result.invalidMigrations.size() + " invalid migration(s)");
        }
    }
}
