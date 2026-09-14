package com.yuegang.zhihui.auth.domain;

public enum LoginRateLimitDimension {
    NONE,
    PRINCIPAL,
    IP,
    BOTH
}
