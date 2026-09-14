package com.yuegang.zhihui.common.mq;

import java.util.regex.Pattern;

/** Base exception carrying only a stable, non-sensitive failure code. */
public abstract class MessageHandlingException extends RuntimeException {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private final String failureCode;

    protected MessageHandlingException(String failureCode) {
        super(validate(failureCode));
        this.failureCode = failureCode;
    }

    public final String failureCode() {
        return failureCode;
    }

    private static String validate(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("failureCode must be a stable uppercase code");
        }
        return code;
    }
}
