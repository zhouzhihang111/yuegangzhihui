package com.yuegang.zhihui.common.core;

/** State persisted for a unique idempotency key. */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
