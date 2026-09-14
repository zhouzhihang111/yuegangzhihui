package com.yuegang.zhihui.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringDataRedisLockCommandsTest {

    @Test
    void releaseAndRenewScriptsCompareOwnerBeforeMutation() {
        assertThat(SpringDataRedisLockCommands.RELEASE_IF_OWNER.getScriptAsString())
                .contains("redis.call('get', KEYS[1]) == ARGV[1]")
                .contains("redis.call('del', KEYS[1])")
                .contains("else return 0");
        assertThat(SpringDataRedisLockCommands.RENEW_IF_OWNER.getScriptAsString())
                .contains("redis.call('get', KEYS[1]) == ARGV[1]")
                .contains("redis.call('pexpire', KEYS[1], ARGV[2])")
                .contains("else return 0");
    }
}
