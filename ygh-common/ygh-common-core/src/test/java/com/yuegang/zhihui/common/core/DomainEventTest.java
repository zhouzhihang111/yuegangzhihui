package com.yuegang.zhihui.common.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DomainEventTest {

    private static final OffsetDateTime OCCURRED_AT =
            OffsetDateTime.of(2026, 7, 11, 15, 0, 0, 0, ZoneOffset.ofHours(8));

    @Test
    void eventCarriesTheCompleteVersionedTraceableEnvelope() {
        DomainEvent<OrderCreatedPayload> event = VersionedDomainEvent.of(new EventMetadata(
                "evt-9007199254740993",
                "ORDER_CREATED",
                1,
                OCCURRED_AT,
                "trace-001",
                "ygh-order-service",
                "order-20260711-001"),
                new OrderCreatedPayload("9007199254740993"));

        assertThat(event.metadata().eventId()).isEqualTo("evt-9007199254740993");
        assertThat(event.metadata().eventType()).isEqualTo("ORDER_CREATED");
        assertThat(event.metadata().eventVersion()).isEqualTo(1);
        assertThat(event.metadata().occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(event.metadata().occurredAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
        assertThat(event.metadata().traceId()).isEqualTo("trace-001");
        assertThat(event.metadata().producer()).isEqualTo("ygh-order-service");
        assertThat(event.metadata().businessKey()).isEqualTo("order-20260711-001");
        assertThat(event.payload().orderId()).isEqualTo("9007199254740993");
        assertThat(event).isInstanceOf(VersionedDomainEvent.class);
    }

    @Test
    void payloadIsDefensivelyCopiedAndCannotBeMutated() {
        var source = new LinkedHashMap<String, Object>();
        source.put("orderId", "order-1");
        var event = eventWith(source);

        source.put("orderId", "tampered");

        assertThat(event.payload()).containsEntry("orderId", "order-1");
        assertThatThrownBy(() -> event.payload().put("extra", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void envelopeRejectsIncompleteOrUnversionedEvents() {
        assertThatThrownBy(() -> metadata(" ", 1, OCCURRED_AT, "trace-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadata("ORDER_CREATED", 0, OCCURRED_AT, "trace-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadata("ORDER_CREATED", 1, null, "trace-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metadata("ORDER_CREATED", 1, OCCURRED_AT, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void envelopeRejectsPoisonMetadataBeforeTransport() {
        assertThatThrownBy(() -> new EventMetadata(
                "x".repeat(129), "ORDER_CREATED", 1, OCCURRED_AT,
                "trace-1", "order-service", "order-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventMetadata(
                "event-1", "ORDER_CREATED", 1, OCCURRED_AT,
                "trace\nforged", "order-service", "order-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventMetadata(
                "event-1", "ORDER_CREATED", 1, OCCURRED_AT,
                "trace-1", "Order Service", "order-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nestedMapAndListPayloadIsDeeplyImmutable() {
        var mutableItem = new LinkedHashMap<String, Object>();
        mutableItem.put("skuId", "sku-1");
        var mutableItems = new ArrayList<Map<String, Object>>();
        mutableItems.add(mutableItem);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("items", mutableItems);

        var event = eventWith(payload);
        mutableItem.put("skuId", "tampered");
        mutableItems.add(Map.of("skuId", "sku-2"));

        @SuppressWarnings("unchecked")
        var storedItems = (List<Map<String, Object>>) event.payload().get("items");
        assertThat(storedItems).containsExactly(Map.of("skuId", "sku-1"));
        assertThatThrownBy(() -> storedItems.add(Map.of("skuId", "sku-3")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> storedItems.getFirst().put("skuId", "tampered-again"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void linkedHashMapPayloadRetainsItsDeclaredMapTypeWithoutClassCastFailure() {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("orderId", "order-1");

        DomainEvent<Map<String, Object>> event =
                VersionedDomainEvent.ofMap(
                        metadata("ORDER_CREATED", 1, OCCURRED_AT, "trace-1"), payload);

        assertThat(event.payload()).containsEntry("orderId", "order-1");
        assertThat(event.payload()).isInstanceOf(Map.class);
    }

    @Test
    void onlyTypedDtoAndMapFactoriesArePublicConstructionEntrypoints() {
        var publicStaticFactoryNames = Arrays.stream(VersionedDomainEvent.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .map(method -> method.getName())
                .toList();

        assertThat(publicStaticFactoryNames).containsExactlyInAnyOrder("of", "ofMap");
        assertThat(Arrays.stream(VersionedDomainEvent.class.getDeclaredConstructors()))
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers()));
    }

    private static DomainEvent<Map<String, Object>> eventWith(Map<String, Object> payload) {
        return VersionedDomainEvent.ofMap(
                metadata("ORDER_CREATED", 1, OCCURRED_AT, "trace-1"), payload);
    }

    private static EventMetadata metadata(
            String eventType, int eventVersion, OffsetDateTime occurredAt, String traceId) {
        return new EventMetadata(
                "evt-1", eventType, eventVersion, occurredAt, traceId, "ygh-order-service", "order-1");
    }

    private record OrderCreatedPayload(String orderId) implements ImmutableEventPayload {
    }
}
