package com.yuegang.zhihui.common.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Default type-safe implementation of the domain-event contract. */
public final class VersionedDomainEvent<T> implements DomainEvent<T> {

    private final EventMetadata metadata;
    private final T payload;

    private VersionedDomainEvent(EventMetadata metadata, T payload) {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        this.metadata = metadata;
        this.payload = payload;
    }

    public static <T extends ImmutableEventPayload> VersionedDomainEvent<T> of(
            EventMetadata metadata,
            T payload
    ) {
        return new VersionedDomainEvent<>(metadata, payload);
    }

    public static VersionedDomainEvent<Map<String, Object>> ofMap(
            EventMetadata metadata,
            Map<String, Object> payload
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        return new VersionedDomainEvent<>(metadata, immutableMap(payload));
    }

    @Override
    public EventMetadata metadata() {
        return metadata;
    }

    @Override
    public T payload() {
        return payload;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(source.size());
        source.forEach((key, value) -> copy.put(key, immutableNestedValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableNestedValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<Object, Object> copy = new LinkedHashMap<>(map.size());
            map.forEach((key, item) -> copy.put(key, immutableNestedValue(item)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(immutableNestedValue(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            LinkedHashSet<Object> copy = new LinkedHashSet<>(set.size());
            set.forEach(item -> copy.add(immutableNestedValue(item)));
            return Collections.unmodifiableSet(copy);
        }
        if (value != null && value.getClass().isArray()) {
            throw new IllegalArgumentException("arrays are not supported in map event payloads");
        }
        return value;
    }
}
