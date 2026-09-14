package com.yuegang.zhihui.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RedisDistributedLockTest {

    private static final String KEY = "ygh:test:inventory:reserve-lock:sku-1";

    @Test
    void acquiresWithUniqueOwnerAndOnlyOwnerCanRelease() {
        var commands = new FakeCommands();
        var locks = new RedisDistributedLock(commands, new RedisKeyBuilder(), sequenceOwners());

        var first = locks.tryAcquire(KEY, Duration.ofSeconds(10)).orElseThrow();
        assertThat(first.owner()).isEqualTo("owner-00000000000000000000000001");
        assertThat(locks.tryAcquire(KEY, Duration.ofSeconds(10))).isEmpty();

        var forged = new RedisLockHandle(KEY, "owner-00000000000000000000000999", Duration.ofSeconds(10));
        assertThat(locks.release(forged)).isFalse();
        assertThat(commands.values).containsEntry(KEY, first.owner());
        assertThat(locks.release(first)).isTrue();
        assertThat(commands.values).doesNotContainKey(KEY);
    }

    @Test
    void renewsLeaseOnlyWhenOwnerStillMatches() {
        var commands = new FakeCommands();
        var locks = new RedisDistributedLock(commands, new RedisKeyBuilder(), sequenceOwners());
        var handle = locks.tryAcquire(KEY, Duration.ofSeconds(5)).orElseThrow();

        assertThat(locks.renew(handle, Duration.ofSeconds(20))).isTrue();
        commands.values.put(KEY, "another-owner-0000000000000000000001");
        assertThat(locks.renew(handle, Duration.ofSeconds(20))).isFalse();
        assertThat(locks.release(handle)).isFalse();
    }

    @Test
    void rejectsMalformedKeyOwnerAndUnboundedLease() {
        var locks = new RedisDistributedLock(new FakeCommands(), new RedisKeyBuilder(), sequenceOwners());

        assertThatThrownBy(() -> locks.tryAcquire("lock:sku-1", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> locks.tryAcquire(KEY, Duration.ofMillis(999)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> locks.tryAcquire(KEY, Duration.ofMinutes(6)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisLockHandle(KEY, "short", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownerCapabilityIsRedactedFromLogsAndJson() {
        var handle = new RedisLockHandle(
                KEY, "owner-00000000000000000000000001", Duration.ofSeconds(10));

        assertThat(handle.toString()).contains("[REDACTED]").doesNotContain(handle.owner());
        assertThat(JsonMapper.builder().build().writeValueAsString(handle))
                .doesNotContain(handle.owner())
                .contains("\"key\"")
                .contains("\"lease\"");
    }

    private static LockOwnerTokenGenerator sequenceOwners() {
        return () -> "owner-00000000000000000000000001";
    }

    private static final class FakeCommands implements RedisLockCommands {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public boolean setIfAbsent(String key, String owner, Duration lease) {
            return values.putIfAbsent(key, owner) == null;
        }

        @Override
        public boolean releaseIfOwner(String key, String owner) {
            return values.remove(key, owner);
        }

        @Override
        public boolean renewIfOwner(String key, String owner, Duration lease) {
            return owner.equals(values.get(key));
        }
    }
}
