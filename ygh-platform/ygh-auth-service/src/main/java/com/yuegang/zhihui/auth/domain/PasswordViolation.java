package com.yuegang.zhihui.auth.domain;

public enum PasswordViolation {
    TOO_SHORT,
    TOO_LONG,
    CONTROL_CHARACTER,
    COMMON_PASSWORD
}
