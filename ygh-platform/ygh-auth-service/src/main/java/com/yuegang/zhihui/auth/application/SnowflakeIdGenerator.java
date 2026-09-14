package com.yuegang.zhihui.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Thread-safe 64-bit IDs with explicit worker ownership and rollback protection. */
public final class SnowflakeIdGenerator {
    private static final long EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final long MAX_SEQUENCE = 4095;
    private final long workerId;
    private final Clock clock;
    private long lastMillis = -1;
    private long sequence;

    public SnowflakeIdGenerator(long workerId, Clock clock) {
        if (workerId < 0 || workerId > 1023) throw new IllegalArgumentException("workerId must be between 0 and 1023");
        this.workerId = workerId;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized long nextId() {
        long now = clock.millis();
        if (now < EPOCH_MILLIS) throw new IllegalStateException("clock is before the configured epoch");
        if (now < lastMillis) throw new IllegalStateException("clock moved backwards");
        if (now == lastMillis) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) now = waitForNextMillis(now);
        } else {
            sequence = 0;
        }
        lastMillis = now;
        return ((now - EPOCH_MILLIS) << 22) | (workerId << 12) | sequence;
    }

    private long waitForNextMillis(long current) {
        long deadline = System.nanoTime() + 10_000_000L;
        long now;
        do {
            Thread.onSpinWait();
            now = clock.millis();
            if (now < current) throw new IllegalStateException("clock moved backwards");
            if (System.nanoTime() >= deadline) throw new IllegalStateException("id sequence capacity exhausted");
        } while (now == current);
        return now;
    }
}
