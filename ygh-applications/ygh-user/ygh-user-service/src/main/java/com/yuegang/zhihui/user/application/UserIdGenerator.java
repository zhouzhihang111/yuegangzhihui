package com.yuegang.zhihui.user.application;

import java.time.*;

public final class UserIdGenerator {
    private static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private final long worker; private final Clock clock; private long last = -1, sequence;
    public UserIdGenerator(long worker, Clock clock) {
        if (worker < 0 || worker > 1023) throw new IllegalArgumentException("worker must be between 0 and 1023");
        this.worker = worker; this.clock = clock;
    }
    public synchronized long nextId() {
        long now = clock.millis();
        if (now < EPOCH || now < last) throw new IllegalStateException("system clock is invalid");
        if (now == last) { sequence = (sequence + 1) & 4095; if (sequence == 0) throw new IllegalStateException("id capacity exhausted"); }
        else sequence = 0;
        last = now; return ((now - EPOCH) << 22) | (worker << 12) | sequence;
    }
}
