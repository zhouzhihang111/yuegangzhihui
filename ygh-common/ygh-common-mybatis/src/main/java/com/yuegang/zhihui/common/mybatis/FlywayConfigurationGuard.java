package com.yuegang.zhihui.common.mybatis;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;

/** Validates the final Flyway configuration immediately before any migration runs. */
public final class FlywayConfigurationGuard {

    private static final String DEFAULT_LOCATION = "classpath:db/migration";

    private final Set<String> approvedLocations;

    public FlywayConfigurationGuard() {
        this(Set.of(DEFAULT_LOCATION));
    }

    public FlywayConfigurationGuard(Set<String> approvedLocations) {
        Objects.requireNonNull(approvedLocations, "approvedLocations must not be null");
        if (approvedLocations.isEmpty()) {
            throw new IllegalArgumentException("approvedLocations must not be empty");
        }
        this.approvedLocations = approvedLocations.stream()
                .map(Location::new)
                .map(Location::getDescriptor)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validateOrThrow(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Set<String> configuredLocations = Set.of(configuration.getLocations()).stream()
                .map(Location::getDescriptor)
                .collect(Collectors.toUnmodifiableSet());
        boolean safe = configuration.isValidateMigrationNaming()
                && configuration.isValidateOnMigrate()
                && configuration.isCleanDisabled()
                && !configuration.isOutOfOrder()
                && !configuration.isBaselineOnMigrate()
                && configuration.getIgnoreMigrationPatterns().length == 0
                && configuredLocations.equals(approvedLocations);
        if (!safe) {
            throw new MigrationPolicyException(
                    MigrationViolationCode.UNSAFE_CONFIGURATION,
                    "final Flyway configuration violates the enterprise migration policy");
        }
    }

    public String toPolicyResourcePath(Configuration configuration, String filepath) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(filepath, "filepath must not be null");
        for (Location location : configuration.getLocations()) {
            if (location.matchesPath(filepath)) {
                String relativePath = location.getPathRelativeToThis(filepath).replace('\\', '/');
                String marker = "/db/migration/";
                int markerIndex = relativePath.lastIndexOf(marker);
                if (markerIndex >= 0) {
                    relativePath = relativePath.substring(markerIndex + marker.length());
                } else if (relativePath.startsWith("db/migration/")) {
                    relativePath = relativePath.substring("db/migration/".length());
                }
                return "db/migration/" + relativePath;
            }
        }
        throw new MigrationPolicyException(
                MigrationViolationCode.UNSAFE_CONFIGURATION,
                "resolved migration is outside the approved Flyway location");
    }
}
