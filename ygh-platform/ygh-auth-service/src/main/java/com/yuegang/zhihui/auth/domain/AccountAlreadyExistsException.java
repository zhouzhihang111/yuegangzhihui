package com.yuegang.zhihui.auth.domain;

public final class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException(Throwable cause) { super("account already exists", cause); }
}
