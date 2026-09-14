package com.yuegang.zhihui.common.mq;

/** Broker adapter decision after one delivery attempt. */
public enum MessageConsumptionResult {
    ACKNOWLEDGED,
    DUPLICATE,
    RETRY,
    DEAD_LETTERED
}
