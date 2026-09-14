package com.yuegang.zhihui.common.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YghFlywayMigrationStrategyTest {

    @TempDir
    private Path migrations;

    @Test
    void scansRealResolvedMigrationsAndMigratesIdempotently() throws Exception {
        write("V1__create_sample.sql", "CREATE TABLE sample (id BIGINT PRIMARY KEY);");
        var flyway = safeFlyway("safe");
        var strategy = strategy();

        strategy.migrate(flyway);
        strategy.migrate(flyway);

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void rejectsRepeatableMigrationFromActualFlywayLocation() throws Exception {
        write("V1__create_sample.sql", "CREATE TABLE sample (id BIGINT PRIMARY KEY);");
        write("R__refresh_sample.sql", "SELECT 1;");

        assertThatThrownBy(() -> strategy().migrate(safeFlyway("repeatable")))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.REPEATABLE_SCRIPT_FORBIDDEN);
    }

    @Test
    void rejectsLowerVersionAddedAfterHigherVersionWasApplied() throws Exception {
        write("V2__create_sample.sql", "CREATE TABLE sample (id BIGINT PRIMARY KEY);");
        var flyway = safeFlyway("history_insert");
        strategy().migrate(flyway);
        write("V1__late_history_insert.sql", "CREATE TABLE late_table (id BIGINT PRIMARY KEY);");

        assertThatThrownBy(() -> strategy().migrate(safeFlyway("history_insert")))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.OUT_OF_ORDER_VERSION);
    }

    @Test
    void rejectsFinalConfigurationChangedAfterSafetyCustomizer() throws Exception {
        write("V1__create_sample.sql", "CREATE TABLE sample (id BIGINT PRIMARY KEY);");
        var unsafe = Flyway.configure()
                .dataSource(url("unsafe"), "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath())
                .validateMigrationNaming(false)
                .validateOnMigrate(false)
                .cleanDisabled(false)
                .outOfOrder(true)
                .baselineOnMigrate(true)
                .load();

        assertThatThrownBy(() -> strategy().migrate(unsafe))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.UNSAFE_CONFIGURATION);
    }

    @Test
    void rejectsUnapprovedLocationAndNestedMigrationDirectory() throws Exception {
        write("V1__create_sample.sql", "CREATE TABLE sample (id BIGINT PRIMARY KEY);");
        assertThatThrownBy(() -> defaultStrategy().migrate(safeFlyway("custom_location")))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.UNSAFE_CONFIGURATION);

        Files.createDirectories(migrations.resolve("nested"));
        Files.move(
                migrations.resolve("V1__create_sample.sql"),
                migrations.resolve("nested/V1__create_sample.sql"));
        assertThatThrownBy(() -> strategy().migrate(safeFlyway("nested_location")))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.INVALID_NAME);
    }

    @Test
    void rejectsJavaBasedMigration() {
        var flyway = Flyway.configure()
                .dataSource(url("java_migration"), "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath())
                .javaMigrations(new V1__java_migration())
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .outOfOrder(false)
                .baselineOnMigrate(false)
                .ignoreMigrationPatterns(new String[0])
                .load();

        assertThatThrownBy(() -> strategy().migrate(flyway))
                .isInstanceOf(MigrationPolicyException.class)
                .extracting(error -> ((MigrationPolicyException) error).code())
                .isEqualTo(MigrationViolationCode.UNSUPPORTED_MIGRATION_TYPE);
    }

    private YghFlywayMigrationStrategy strategy() {
        return new YghFlywayMigrationStrategy(
                new FlywayMigrationPolicy(),
                new FlywayConfigurationGuard(Set.of(
                        "filesystem:" + migrations.toAbsolutePath())),
                new FlywayHistoryValidator());
    }

    private YghFlywayMigrationStrategy defaultStrategy() {
        return new YghFlywayMigrationStrategy(
                new FlywayMigrationPolicy(),
                new FlywayConfigurationGuard(),
                new FlywayHistoryValidator());
    }

    private Flyway safeFlyway(String databaseName) {
        return Flyway.configure()
                .dataSource(url(databaseName), "sa", "")
                .locations("filesystem:" + migrations.toAbsolutePath())
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .outOfOrder(false)
                .baselineOnMigrate(false)
                .ignoreMigrationPatterns(new String[0])
                .load();
    }

    private String url(String databaseName) {
        return "jdbc:h2:mem:" + databaseName + "_" + Integer.toUnsignedString(migrations.hashCode())
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private void write(String fileName, String sql) throws Exception {
        Files.writeString(migrations.resolve(fileName), sql);
    }
}

final class V1__java_migration extends BaseJavaMigration {

    @Override
    public void migrate(Context context) {
        throw new AssertionError("Java migration must be rejected before execution");
    }
}
