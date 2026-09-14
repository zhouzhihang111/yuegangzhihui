package com.yuegang.zhihui.common.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MutableTestClockTest {

    @Test
    void advancesAndChangesZoneWithoutLosingSharedTime() {
        var clock = MutableTestClock.utc(Instant.parse("2026-07-11T04:00:00Z"));
        var shanghai = clock.withZone(ZoneId.of("Asia/Shanghai"));

        clock.advance(Duration.ofMinutes(5));

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-07-11T04:05:00Z"));
        assertThat(shanghai.instant()).isEqualTo(clock.instant());
        assertThat(shanghai.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
    }

    @Test
    void rejectsBackwardsOrZeroAdvanceButAllowsExplicitReset() {
        var clock = MutableTestClock.utc(Instant.EPOCH);
        assertThatThrownBy(() -> clock.advance(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clock.advance(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);

        clock.set(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void concurrentAdvancesAreLinearizableAndVisibleAcrossZoneViews() throws Exception {
        var clock = MutableTestClock.utc(Instant.EPOCH);
        var zoneView = clock.withZone(ZoneId.of("Asia/Shanghai"));
        int threads = 8;
        int advancesPerThread = 250;
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (int thread = 0; thread < threads; thread++) {
                executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("clock test barrier timed out");
                    }
                    for (int advance = 0; advance < advancesPerThread; advance++) {
                        clock.advance(Duration.ofMillis(1));
                    }
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        }

        assertThat(clock.instant()).isEqualTo(
                Instant.EPOCH.plusMillis((long) threads * advancesPerThread));
        assertThat(zoneView.instant()).isEqualTo(clock.instant());
    }
}
