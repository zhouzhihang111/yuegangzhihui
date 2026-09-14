package com.yuegang.zhihui.common.redis;

import java.time.Duration;

/** Atomic Redis primitives required by the owner-checked distributed lock. */
public interface RedisLockCommands {

    boolean setIfAbsent(String key, String owner, Duration lease);

    boolean releaseIfOwner(String key, String owner);

    boolean renewIfOwner(String key, String owner, Duration lease);
}
