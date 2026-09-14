package com.yuegang.zhihui.user.application;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class UserIdGeneratorTest {
    @Test void createsOrderedIdsForWorkerAndSameMillisecond(){
        var clock=Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"),ZoneOffset.UTC);
        var generator=new UserIdGenerator(2,clock);
        assertThat(generator.nextId()).isPositive(); assertThat(generator.nextId()).isGreaterThan(generator.nextId()-2);
    }
    @Test void rejectsInvalidWorkersAndClockBeforeEpoch(){
        assertThatThrownBy(()->new UserIdGenerator(-1,Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new UserIdGenerator(1024,Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        var generator=new UserIdGenerator(0,Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"),ZoneOffset.UTC));
        assertThatThrownBy(generator::nextId).isInstanceOf(IllegalStateException.class);
    }
}
