package com.yuegang.zhihui.common.mybatis;

/** Stable violation codes emitted by the repository and database migration guards. */
public enum MigrationViolationCode {
    INVALID_NAME,
    UNDO_SCRIPT_FORBIDDEN,
    REPEATABLE_SCRIPT_FORBIDDEN,
    DUPLICATE_VERSION,
    DUPLICATE_RESOURCE,
    OUT_OF_ORDER_VERSION,
    UNSAFE_CONFIGURATION,
    UNSUPPORTED_MIGRATION_TYPE,
    HISTORY_VALIDATION_FAILED
}
