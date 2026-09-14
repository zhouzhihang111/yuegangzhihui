package com.yuegang.zhihui.auth.domain;

public final class PasswordHashCapacityException extends RuntimeException {
    public PasswordHashCapacityException(String message) {
        super(message);
    }

    public PasswordHashCapacityException(String message, Throwable cause) {
        super(message, cause);
    }
}
