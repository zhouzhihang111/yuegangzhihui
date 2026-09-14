package com.yuegang.zhihui.common.security;

/** Resolves whether an account may continue using an otherwise valid token. */
@FunctionalInterface
public interface AccountStatusProvider {

    boolean isEnabled(String userId);
}
