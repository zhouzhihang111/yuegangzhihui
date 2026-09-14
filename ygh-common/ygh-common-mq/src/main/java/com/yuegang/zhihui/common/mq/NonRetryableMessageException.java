package com.yuegang.zhihui.common.mq;

/** Permanent contract or business failure that must be dead-lettered immediately. */
public final class NonRetryableMessageException extends MessageHandlingException {

    public NonRetryableMessageException(String failureCode) {
        super(failureCode);
    }
}
