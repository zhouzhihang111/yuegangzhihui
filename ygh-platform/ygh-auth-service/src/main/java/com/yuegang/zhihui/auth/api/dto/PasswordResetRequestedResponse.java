package com.yuegang.zhihui.auth.api.dto;

/** Public response is intentionally invariant to whether the principal exists. */
public record PasswordResetRequestedResponse(long expiresIn) { }
