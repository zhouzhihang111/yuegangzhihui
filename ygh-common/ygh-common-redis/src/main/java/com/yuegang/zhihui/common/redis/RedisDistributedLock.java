package com.yuegang.zhihui.common.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Short-lived Redis lock whose release and renewal always compare the owner token atomically. */
public final class RedisDistributedLock {

    public static final Duration MIN_LEASE = Duration.ofSeconds(1);
    public static final Duration MAX_LEASE = Duration.ofMinutes(5);

    private final RedisLockCommands commands;
    private final RedisKeyBuilder keys;
    private final LockOwnerTokenGenerator ownerTokens;

    public RedisDistributedLock(
            RedisLockCommands commands,
            RedisKeyBuilder keys,
            LockOwnerTokenGenerator ownerTokens
    ) {
        this.commands = Objects.requireNonNull(commands, "commands must not be null");
        this.keys = Objects.requireNonNull(keys, "keys must not be null");
        this.ownerTokens = Objects.requireNonNull(ownerTokens, "ownerTokens must not be null");
    }

    public Optional<RedisLockHandle> tryAcquire(String key, Duration lease) {
        requireCanonicalKey(key);
        requireLease(lease);
        String owner = ownerTokens.generate();
        var handle = new RedisLockHandle(key, owner, lease);
        return commands.setIfAbsent(key, owner, lease) ? Optional.of(handle) : Optional.empty();
    }

    public boolean release(RedisLockHandle handle) {
        Objects.requireNonNull(handle, "handle must not be null");
        requireCanonicalKey(handle.key());
        return commands.releaseIfOwner(handle.key(), handle.owner());
    }

    public boolean renew(RedisLockHandle handle, Duration lease) {
        Objects.requireNonNull(handle, "handle must not be null");
        requireCanonicalKey(handle.key());
        requireLease(lease);
        return commands.renewIfOwner(handle.key(), handle.owner(), lease);
    }

    static void requireLease(Duration lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        if (lease.compareTo(MIN_LEASE) < 0 || lease.compareTo(MAX_LEASE) > 0) {
            throw new IllegalArgumentException("lease must be between 1 second and 5 minutes");
        }
    }

    private void requireCanonicalKey(String key) {
        if (!keys.isCanonical(key)) {
            throw new IllegalArgumentException("lock key must use the canonical ygh namespace");
        }
    }
}
