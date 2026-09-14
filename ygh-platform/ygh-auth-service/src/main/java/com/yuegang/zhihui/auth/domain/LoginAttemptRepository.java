package com.yuegang.zhihui.auth.domain;

@FunctionalInterface
public interface LoginAttemptRepository {
    void save(LoginAttempt attempt);
}
