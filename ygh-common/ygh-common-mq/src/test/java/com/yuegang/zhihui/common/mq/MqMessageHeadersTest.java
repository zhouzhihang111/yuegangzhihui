package com.yuegang.zhihui.common.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.EventMetadata;
import com.yuegang.zhihui.common.core.DomainEvent;
import com.yuegang.zhihui.common.core.ImmutableEventPayload;
import com.yuegang.zhihui.common.core.VersionedDomainEvent;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MqMessageHeadersTest {

    @Test
    void mapsStableDomainEventEnvelopeWithoutPayloadOrSecrets() {
        var event = event("event-1");

        var headers = MqMessageHeaders.from(event);

        assertThat(headers).containsOnlyKeys(
                "eventId", "eventType", "eventVersion", "occurredAt",
                "traceId", "producer", "businessKey", "contentType");
        assertThat(headers).containsEntry("eventId", "event-1")
                .containsEntry("eventType", "ORDER_CREATED")
                .containsEntry("eventVersion", "1")
                .containsEntry("occurredAt", "2026-07-11T12:00:00+08:00")
                .containsEntry("contentType", "application/json");
        assertThatThrownBy(() -> headers.put("token", "secret"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullEnvelope() {
        assertThatThrownBy(() -> MqMessageHeaders.from(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsCustomPoisonEnvelopeBeforeBrokerOrDeadLetterProcessing() {
        DomainEvent<TestPayload> poison = new DomainEvent<>() {
            @Override
            public EventMetadata metadata() {
                return null;
            }

            @Override
            public TestPayload payload() {
                return new TestPayload("order-1");
            }

            @Override
            public String eventId() {
                return "event\nforged";
            }

            @Override
            public String eventType() {
                return "ORDER_CREATED";
            }

            @Override
            public int eventVersion() {
                return 1;
            }

            @Override
            public OffsetDateTime occurredAt() {
                return OffsetDateTime.parse("2026-07-11T12:00:00+08:00");
            }

            @Override
            public String traceId() {
                return "trace-1";
            }

            @Override
            public String producer() {
                return "order-service";
            }

            @Override
            public String businessKey() {
                return "order-1";
            }
        };

        assertThatThrownBy(() -> MqMessageHeaders.from(poison))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static VersionedDomainEvent<TestPayload> event(String eventId) {
        return VersionedDomainEvent.of(
                new EventMetadata(
                        eventId,
                        "ORDER_CREATED",
                        1,
                        OffsetDateTime.parse("2026-07-11T12:00:00+08:00"),
                        "trace-1",
                        "order-service",
                        "order-1001"),
                new TestPayload("order-1001"));
    }

    private record TestPayload(String orderId) implements ImmutableEventPayload {
    }
}
