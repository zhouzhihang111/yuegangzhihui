package com.yuegang.zhihui.common.mq;

/** Storage or transport infrastructure failure that must always be retried by the broker. */
public final class MessageInfrastructureException extends RuntimeException {

    public MessageInfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
