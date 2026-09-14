package com.yuegang.zhihui.auth.domain;

public enum LoginAttemptResult {
    SUCCESS,
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED,
    RATE_LIMITED,
    SYSTEM_ERROR
}
