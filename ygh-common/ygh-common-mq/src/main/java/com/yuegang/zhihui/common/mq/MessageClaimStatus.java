package com.yuegang.zhihui.common.mq;

/** Durable idempotency claim outcome. */
public enum MessageClaimStatus {
    CLAIMED,
    DUPLICATE,
    IN_PROGRESS
}
