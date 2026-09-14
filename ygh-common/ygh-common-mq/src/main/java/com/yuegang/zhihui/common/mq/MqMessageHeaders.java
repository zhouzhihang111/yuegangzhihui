package com.yuegang.zhihui.common.mq;

import com.yuegang.zhihui.common.core.DomainEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/** Stable transport headers derived from the canonical domain-event envelope. */
public final class MqMessageHeaders {

    private static final DateTimeFormatter STABLE_OFFSET_TIME = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendOffsetId()
            .toFormatter();

    private MqMessageHeaders() {
    }

    public static Map<String, String> from(DomainEvent<?> event) {
        MqEnvelopePolicy.validate(event);
        var headers = new LinkedHashMap<String, String>();
        headers.put("eventId", event.eventId());
        headers.put("eventType", event.eventType());
        headers.put("eventVersion", Integer.toString(event.eventVersion()));
        headers.put("occurredAt", STABLE_OFFSET_TIME.format(event.occurredAt()));
        headers.put("traceId", event.traceId());
        headers.put("producer", event.producer());
        headers.put("businessKey", event.businessKey());
        headers.put("contentType", "application/json");
        return Map.copyOf(headers);
    }
}
