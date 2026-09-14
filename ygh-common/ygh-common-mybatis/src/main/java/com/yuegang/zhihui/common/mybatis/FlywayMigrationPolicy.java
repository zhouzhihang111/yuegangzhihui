package com.yuegang.zhihui.common.mybatis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Repository-level forward-only migration policy for one service-owned database.
 * Database history checks remain delegated to Flyway itself.
 */
public final class FlywayMigrationPolicy {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile(
            "^db/migration/V([1-9][0-9]*)__([a-z][a-z0-9]*(?:_[a-z0-9]+)*)\\.sql$");

    public MigrationDescriptor parse(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        String normalized = normalize(resourcePath);
        if (normalized.isBlank() || normalized.contains("../") || normalized.contains("/../")) {
            throw invalid(normalized.isBlank() ? "<blank>" : normalized);
        }
        if (normalized.startsWith("db/migration/U")) {
            throw new MigrationPolicyException(
                    MigrationViolationCode.UNDO_SCRIPT_FORBIDDEN,
                    "undo migration is forbidden: " + safeDisplay(normalized));
        }
        if (normalized.startsWith("db/migration/R__")) {
            throw new MigrationPolicyException(
                    MigrationViolationCode.REPEATABLE_SCRIPT_FORBIDDEN,
                    "repeatable migration is forbidden: " + safeDisplay(normalized));
        }

        var matcher = VERSIONED_MIGRATION.matcher(normalized);
        if (!matcher.matches()) {
            throw invalid(normalized);
        }

        long version;
        try {
            version = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw invalid(normalized);
        }
        return new MigrationDescriptor(normalized, version, matcher.group(2));
    }

    public MigrationValidationReport validate(Collection<String> resourcePaths) {
        Objects.requireNonNull(resourcePaths, "resourcePaths must not be null");
        var violations = new ArrayList<MigrationViolation>();
        Set<String> seenPaths = new HashSet<>();
        Map<Long, String> pathByVersion = new HashMap<>();

        for (String resourcePath : resourcePaths) {
            if (resourcePath == null) {
                violations.add(new MigrationViolation(
                        MigrationViolationCode.INVALID_NAME,
                        "<null>",
                        "migration path must not be null"));
                continue;
            }

            String normalized = normalize(resourcePath);
            if (!seenPaths.add(normalized)) {
                String displayPath = safeDisplay(normalized);
                violations.add(new MigrationViolation(
                        MigrationViolationCode.DUPLICATE_RESOURCE,
                        displayPath,
                        "migration resource is duplicated: " + displayPath));
                continue;
            }

            try {
                var descriptor = parse(normalized);
                String existingPath = pathByVersion.putIfAbsent(
                        descriptor.version(), descriptor.resourcePath());
                if (existingPath != null) {
                    violations.add(new MigrationViolation(
                            MigrationViolationCode.DUPLICATE_VERSION,
                            descriptor.resourcePath(),
                            "version " + descriptor.version() + " is already used by "
                                    + existingPath));
                }
            } catch (MigrationPolicyException exception) {
                violations.add(new MigrationViolation(
                        exception.code(),
                        safeDisplay(normalized),
                        exception.getMessage()));
            }
        }
        return new MigrationValidationReport(violations);
    }

    public MigrationValidationReport validateNewMigrations(
            Collection<String> resourcePaths,
            long highestAppliedVersion
    ) {
        if (highestAppliedVersion < 0) {
            throw new IllegalArgumentException("highestAppliedVersion must not be negative");
        }
        var violations = new ArrayList<>(validate(resourcePaths).violations());
        for (String resourcePath : resourcePaths) {
            if (resourcePath == null) {
                continue;
            }
            try {
                var descriptor = parse(resourcePath);
                if (descriptor.version() <= highestAppliedVersion) {
                    violations.add(new MigrationViolation(
                            MigrationViolationCode.OUT_OF_ORDER_VERSION,
                            descriptor.resourcePath(),
                            "new version " + descriptor.version()
                                    + " must be greater than applied version "
                                    + highestAppliedVersion));
                }
            } catch (MigrationPolicyException ignored) {
                // The base validation report already contains the naming violation.
            }
        }
        return new MigrationValidationReport(violations);
    }

    private static String normalize(String resourcePath) {
        return resourcePath.replace('\\', '/');
    }

    private static MigrationPolicyException invalid(String path) {
        String displayPath = safeDisplay(path);
        return new MigrationPolicyException(
                MigrationViolationCode.INVALID_NAME,
                "migration name must match db/migration/V<positive integer>__<lower_snake_case>.sql: "
                        + displayPath);
    }

    private static String safeDisplay(String path) {
        if (path == null || path.isBlank()) {
            return "<blank>";
        }
        String normalized = normalize(path).replaceAll("[\\p{Cntrl}]", "?");
        int separator = normalized.lastIndexOf('/');
        String fileName = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        if (fileName.isBlank()) {
            fileName = "<unnamed>";
        }
        return fileName.length() <= 128 ? fileName : fileName.substring(0, 128);
    }
}
