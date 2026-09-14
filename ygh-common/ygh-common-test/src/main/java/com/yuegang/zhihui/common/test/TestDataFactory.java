package com.yuegang.zhihui.common.test;

import java.util.concurrent.atomic.AtomicLong;

/** Deterministic factory that never emits real customer or employee identity data. */
public final class TestDataFactory {

    private final long seed;
    private final AtomicLong sequence = new AtomicLong();

    public TestDataFactory(long seed) {
        if (seed < 0L || seed > 999_999_999L) {
            throw new IllegalArgumentException("seed must be between 0 and 999999999");
        }
        this.seed = seed;
    }

    public TestUserData nextUser() {
        long number = sequence.incrementAndGet();
        String suffix = seed + "-" + number;
        return new TestUserData(
                "test-user-" + suffix,
                "test_user_" + seed + '_' + number,
                "test-user-" + suffix + "@example.test",
                "测试用户" + suffix);
    }

    public String nextOrderId() {
        return "test-order-" + seed + '-' + sequence.incrementAndGet();
    }
}
