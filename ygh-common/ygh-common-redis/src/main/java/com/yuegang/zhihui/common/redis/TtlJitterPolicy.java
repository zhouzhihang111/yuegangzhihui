package com.yuegang.zhihui.common.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** Adds bounded symmetric jitter so entries do not expire in one synchronized wave. */
public final class TtlJitterPolicy {

    public static final double DEFAULT_RATIO = 0.10d;
    public static final Duration MAX_BASE_TTL = Duration.ofDays(3650);
    private static final double MAX_RATIO = 0.50d;

    private final double ratio;
    private final DoubleSupplier random;

    public TtlJitterPolicy() {
        this(DEFAULT_RATIO);
    }

    public TtlJitterPolicy(double ratio) {
        this(ratio, () -> ThreadLocalRandom.current().nextDouble());
    }

    TtlJitterPolicy(double ratio, DoubleSupplier random) {
        if (!Double.isFinite(ratio) || ratio < 0.0d || ratio > MAX_RATIO) {
            throw new IllegalArgumentException("ratio must be between 0.0 and 0.5");
        }
        this.ratio = ratio;
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /** Applies symmetric jitter to rebuildable cache entries only. */
    public Duration cacheTtl(Duration baseTtl) {
        long baseMillis = requireBaseTtl(baseTtl);
        long windowMillis = jitterWindowMillis(baseMillis);
        long offset = -windowMillis + randomOffset(Math.multiplyExact(windowMillis, 2L));
        return Duration.ofMillis(Math.max(1L, Math.addExact(baseMillis, offset)));
    }

    /** Adds jitter without shortening a mandatory security or idempotency retention period. */
    public Duration minimumRetentionTtl(Duration minimumTtl) {
        long baseMillis = requireBaseTtl(minimumTtl);
        long windowMillis = jitterWindowMillis(baseMillis);
        return Duration.ofMillis(Math.addExact(baseMillis, randomOffset(windowMillis)));
    }

    private long requireBaseTtl(Duration baseTtl) {
        Objects.requireNonNull(baseTtl, "baseTtl must not be null");
        if (baseTtl.compareTo(Duration.ofMillis(1)) < 0
                || baseTtl.compareTo(MAX_BASE_TTL) > 0) {
            throw new IllegalArgumentException(
                    "baseTtl must be between one millisecond and " + MAX_BASE_TTL);
        }
        return baseTtl.toMillis();
    }

    private long jitterWindowMillis(long baseMillis) {
        return Math.round(Math.multiplyExact(baseMillis, 10_000L) * ratio / 10_000.0d);
    }

    private long randomOffset(long maximumInclusive) {
        double sample = random.getAsDouble();
        if (!Double.isFinite(sample) || sample < 0.0d || sample >= 1.0d) {
            throw new IllegalStateException("random source must return a value in [0, 1)");
        }
        return (long) Math.floor(sample * Math.addExact(maximumInclusive, 1L));
    }
}
