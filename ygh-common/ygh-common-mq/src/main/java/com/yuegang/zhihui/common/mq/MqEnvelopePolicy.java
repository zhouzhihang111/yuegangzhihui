package com.yuegang.zhihui.common.mq;

import com.yuegang.zhihui.common.core.DomainEvent;
import java.util.Objects;

/** Defensive transport boundary for custom DomainEvent implementations. */
public final class MqEnvelopePolicy {

    private MqEnvelopePolicy() {
    }

    public static void validate(DomainEvent<?> event) {
        Objects.requireNonNull(event, "event must not be null");
        require(event.eventId(), "eventId", "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
        require(event.eventType(), "eventType", "[A-Z][A-Z0-9_]{0,63}");
        require(event.traceId(), "traceId", "[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
        require(event.producer(), "producer", "[a-z0-9][a-z0-9-]{0,63}");
        require(event.businessKey(), "businessKey", "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
        if (event.eventVersion() < 1 || event.occurredAt() == null || event.payload() == null) {
            throw new IllegalArgumentException("event envelope is incomplete");
        }
    }

    private static void require(String value, String name, String pattern) {
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException(name + " is not safe for MQ transport");
        }
    }
}
