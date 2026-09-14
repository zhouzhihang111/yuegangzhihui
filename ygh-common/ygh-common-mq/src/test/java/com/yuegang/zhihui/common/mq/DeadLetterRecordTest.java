package com.yuegang.zhihui.common.mq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeadLetterRecordTest {

    @Test
    void rejectsUnstableFailureCodeAndInvalidAttempt() {
        assertThatThrownBy(() -> new DeadLetterRecord(
                "event-1", "ORDER_CREATED", 1, "orders", "order-1", "trace-1",
                0, "invalid message", Instant.parse("2026-07-11T04:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeadLetterRecord(
                "event-1", "ORDER_CREATED", 1, "orders", "order-1", "trace-1",
                1, "PASSWORD=secret", Instant.parse("2026-07-11T04:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
