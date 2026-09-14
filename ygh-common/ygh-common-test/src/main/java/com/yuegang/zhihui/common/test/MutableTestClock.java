package com.yuegang.zhihui.common.test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe controllable Clock shared by deterministic tests. */
public final class MutableTestClock extends Clock {

    private final AtomicReference<Instant> current;
    private final ZoneId zone;

    private MutableTestClock(AtomicReference<Instant> current, ZoneId zone) {
        this.current = Objects.requireNonNull(current, "current must not be null");
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
    }

    public static MutableTestClock utc(Instant initialInstant) {
        return new MutableTestClock(
                new AtomicReference<>(Objects.requireNonNull(
                        initialInstant, "initialInstant must not be null")),
                ZoneOffset.UTC);
    }

    public void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("advance duration must be positive");
        }
        current.updateAndGet(instant -> instant.plus(duration));
    }

    public void set(Instant instant) {
        current.set(Objects.requireNonNull(instant, "instant must not be null"));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public MutableTestClock withZone(ZoneId newZone) {
        return new MutableTestClock(current, Objects.requireNonNull(newZone, "newZone must not be null"));
    }

    @Override
    public Instant instant() {
        return current.get();
    }
}
