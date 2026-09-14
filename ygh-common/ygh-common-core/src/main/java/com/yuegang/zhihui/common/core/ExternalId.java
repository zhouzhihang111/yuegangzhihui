package com.yuegang.zhihui.common.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * External identifier serialized as text so values never lose precision in
 * JavaScript or heterogeneous clients.
 */
public record ExternalId(String value) {

    public ExternalId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("external id must not be blank");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException("external id must not contain surrounding whitespace");
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ExternalId of(String value) {
        return new ExternalId(value);
    }

    @Override
    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
