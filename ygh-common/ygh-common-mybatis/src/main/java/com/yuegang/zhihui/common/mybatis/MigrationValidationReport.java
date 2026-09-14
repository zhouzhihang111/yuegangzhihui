package com.yuegang.zhihui.common.mybatis;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable, stable-order report suitable for CI output. */
public record MigrationValidationReport(List<MigrationViolation> violations) {

    private static final Comparator<MigrationViolation> ORDER =
            Comparator.comparing(MigrationViolation::code)
                    .thenComparing(MigrationViolation::resourcePath);

    public MigrationValidationReport {
        Objects.requireNonNull(violations, "violations must not be null");
        violations = violations.stream().sorted(ORDER).toList();
    }

    public boolean valid() {
        return violations.isEmpty();
    }

    public void throwIfInvalid() {
        if (!valid()) {
            var first = violations.getFirst();
            throw new MigrationPolicyException(
                    first.code(),
                    "migration policy failed with " + violations.size() + " violation(s): "
                            + first.message());
        }
    }
}
