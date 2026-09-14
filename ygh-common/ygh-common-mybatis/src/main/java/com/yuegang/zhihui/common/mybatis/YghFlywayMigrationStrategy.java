package com.yuegang.zhihui.common.mybatis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.InfoOutput;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

/**
 * Final migration gate that validates effective configuration, actual resolved
 * resources and applied history before delegating to Flyway migrate.
 */
public final class YghFlywayMigrationStrategy implements FlywayMigrationStrategy {

    private final FlywayMigrationPolicy migrationPolicy;
    private final FlywayConfigurationGuard configurationGuard;
    private final FlywayHistoryValidator historyValidator;

    public YghFlywayMigrationStrategy(
            FlywayMigrationPolicy migrationPolicy,
            FlywayConfigurationGuard configurationGuard,
            FlywayHistoryValidator historyValidator
    ) {
        this.migrationPolicy = Objects.requireNonNull(
                migrationPolicy, "migrationPolicy must not be null");
        this.configurationGuard = Objects.requireNonNull(
                configurationGuard, "configurationGuard must not be null");
        this.historyValidator = Objects.requireNonNull(
                historyValidator, "historyValidator must not be null");
    }

    @Override
    public void migrate(Flyway flyway) {
        Objects.requireNonNull(flyway, "flyway must not be null");
        configurationGuard.validateOrThrow(flyway.getConfiguration());

        var info = flyway.info().getInfoResult();
        List<String> allResources = new ArrayList<>();
        List<String> notAppliedResources = new ArrayList<>();
        long highestAppliedVersion = 0L;

        for (InfoOutput migration : info.migrations) {
            if (!isSqlMigration(migration)) {
                throw new MigrationPolicyException(
                        MigrationViolationCode.UNSUPPORTED_MIGRATION_TYPE,
                        "only SQL migrations are supported");
            }
            if (isRepeatable(migration)) {
                throw new MigrationPolicyException(
                        MigrationViolationCode.REPEATABLE_SCRIPT_FORBIDDEN,
                        "repeatable migration is forbidden");
            }
            if (isUndo(migration)) {
                throw new MigrationPolicyException(
                        MigrationViolationCode.UNDO_SCRIPT_FORBIDDEN,
                        "undo migration is forbidden");
            }
            if (migration.filepath != null && migration.filepath.endsWith(".sql")) {
                String policyPath = configurationGuard.toPolicyResourcePath(
                        flyway.getConfiguration(), migration.filepath);
                allResources.add(policyPath);
                if (migration.installedOnUTC == null) {
                    notAppliedResources.add(policyPath);
                }
            }
            String resolvedVersion = resolvedVersion(migration);
            if (resolvedVersion != null && isApplied(migration)) {
                highestAppliedVersion = Math.max(
                        highestAppliedVersion,
                        parseAppliedVersion(resolvedVersion));
            }
        }

        migrationPolicy.validate(allResources).throwIfInvalid();
        for (InfoOutput migration : info.migrations) {
            String resolvedVersion = resolvedVersion(migration);
            if (resolvedVersion != null && !isApplied(migration)
                    && parseAppliedVersion(resolvedVersion) <= highestAppliedVersion) {
                throw new MigrationPolicyException(
                        MigrationViolationCode.OUT_OF_ORDER_VERSION,
                        "resolved migration version must be greater than applied history");
            }
        }
        migrationPolicy.validateNewMigrations(
                notAppliedResources, highestAppliedVersion).throwIfInvalid();
        flyway.migrate();
        historyValidator.validateOrThrow(flyway);
    }

    private static boolean isRepeatable(InfoOutput migration) {
        boolean repeatableCategory = migration.category != null
                && migration.category.toLowerCase().contains("repeatable");
        boolean sqlWithoutVersion = resolvedVersion(migration) == null
                && migration.filepath != null
                && migration.filepath.toLowerCase().endsWith(".sql");
        return repeatableCategory || sqlWithoutVersion;
    }

    private static boolean isUndo(InfoOutput migration) {
        if (migration.filepath == null) {
            return false;
        }
        String normalized = migration.filepath.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String fileName = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        return fileName.startsWith("U");
    }

    private static boolean isSqlMigration(InfoOutput migration) {
        return migration.type != null && migration.type.equalsIgnoreCase("SQL");
    }

    private static long parseAppliedVersion(String version) {
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException exception) {
            throw new MigrationPolicyException(
                    MigrationViolationCode.INVALID_NAME,
                    "applied migration uses unsupported version format");
        }
    }

    private static String resolvedVersion(InfoOutput migration) {
        if (migration.rawVersion != null && !migration.rawVersion.isBlank()) {
            return migration.rawVersion;
        }
        return migration.version == null || migration.version.isBlank() ? null : migration.version;
    }

    private static boolean isApplied(InfoOutput migration) {
        if (migration.installedOnUTC != null && !migration.installedOnUTC.isBlank()) {
            return true;
        }
        return migration.state != null
                && migration.state.toLowerCase().contains("success");
    }
}
