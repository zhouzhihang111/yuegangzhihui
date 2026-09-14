package com.yuegang.zhihui.common.mybatis;

import java.util.Objects;

/** Fail-fast migration policy error that never includes database credentials. */
public final class MigrationPolicyException extends RuntimeException {

    private final MigrationViolationCode code;

    public MigrationPolicyException(MigrationViolationCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public MigrationViolationCode code() {
        return code;
    }
}
