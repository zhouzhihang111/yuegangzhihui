package com.yuegang.zhihui.common.core;

import java.time.OffsetDateTime;

/** Base contract for every versioned cross-service domain event. */
public interface DomainEvent<T> {

    EventMetadata metadata();

    T payload();

    default String eventId() {
        return metadata().eventId();
    }

    default String eventType() {
        return metadata().eventType();
    }

    default int eventVersion() {
        return metadata().eventVersion();
    }

    default OffsetDateTime occurredAt() {
        return metadata().occurredAt();
    }

    default String traceId() {
        return metadata().traceId();
    }

    default String producer() {
        return metadata().producer();
    }

    default String businessKey() {
        return metadata().businessKey();
    }
}
