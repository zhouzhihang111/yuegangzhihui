package com.yuegang.zhihui.common.mq;

/** Business operation that must run in the store's local idempotency transaction. */
@FunctionalInterface
public interface MessageBusinessOperation {

    void execute() throws Exception;
}
