package com.yuegang.zhihui.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlywayHistoryValidatorTest {

    @TempDir
    private Path migrations;

    @Test
    void validatesAnAppliedImmutableMigrationAndSecondMigrateIsIdempotent() throws Exception {
        write("V1__create_sample.sql", "CREATE TABLE sample (id BIGINT PRIMARY KEY);");
        var flyway = flyway();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThatCode(() -> new FlywayHistoryValidator().validateOrThrow(flyway))
                .doesNotThrowAnyException();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void checksumChangeAfterMigrationFailsValidation() throws Exception {
        var migration = write(
                "V1__create_sample.sql",
                "CREATE TABLE sample (id BIGINT PRIMARY KEY);");
        flyway().migrate();
        Files.writeString(
                migration,
                "CREATE TABLE sample (id BIGINT PRIMARY KEY, changed_name VARCHAR(20));");

        var changedFlyway = flyway();

        assertThat(changedFlyway.validateWithResult().validationSuccessful).isFalse();
        assertThatThrownBy(() -> new FlywayHistoryValidator().validateOrThrow(changedFlyway))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.HISTORY_VALIDATION_FAILED);
    }

    @Test
    void missingAppliedMigrationFailsValidation() throws Exception {
        var migration = write(
                "V1__create_sample.sql",
                "CREATE TABLE sample (id BIGINT PRIMARY KEY);");
        flyway().migrate();
        Files.delete(migration);

        assertThatThrownBy(() -> new FlywayHistoryValidator().validateOrThrow(flyway()))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.HISTORY_VALIDATION_FAILED);
    }

    @Test
    void validatorApiDoesNotOfferAutomaticRepairOrClean() {
        assertThat(FlywayHistoryValidator.class.getDeclaredMethods())
                .filteredOn(method -> !method.isSynthetic())
                .extracting(method -> method.getName())
                .containsExactly("validateOrThrow");
    }

    private Path write(String fileName, String sql) throws Exception {
        return Files.writeString(migrations.resolve(fileName), sql);
    }

    private Flyway flyway() {
        String databaseName = "flyway_" + Integer.toUnsignedString(migrations.hashCode());
        return Flyway.configure()
                .dataSource("jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath())
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .outOfOrder(false)
                .ignoreMigrationPatterns(new String[0])
                .load();
    }
}
