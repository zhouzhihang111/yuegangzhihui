package com.yuegang.zhihui.common.redis;

/** Generates an unpredictable, per-acquisition lock owner token. */
@FunctionalInterface
public interface LockOwnerTokenGenerator {

    String generate();
}
