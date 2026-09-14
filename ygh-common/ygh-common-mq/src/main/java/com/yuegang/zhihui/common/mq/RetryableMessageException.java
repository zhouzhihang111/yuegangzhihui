package com.yuegang.zhihui.common.mq;

/** Explicit transient failure that may be retried until the configured attempt limit. */
public final class RetryableMessageException extends MessageHandlingException {

    public RetryableMessageException(String failureCode) {
        super(failureCode);
    }
}
