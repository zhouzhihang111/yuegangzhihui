package com.yuegang.zhihui.common.mq;

import com.yuegang.zhihui.common.core.DomainEvent;

/** Typed domain-event handler invoked inside the durable consumption transaction. */
@FunctionalInterface
public interface MessageHandler<T> {

    void handle(DomainEvent<T> event) throws Exception;
}
