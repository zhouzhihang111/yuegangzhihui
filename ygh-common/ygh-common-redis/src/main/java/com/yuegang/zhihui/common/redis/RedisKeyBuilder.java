package com.yuegang.zhihui.common.redis;

import java.util.Objects;
import java.util.regex.Pattern;

/** Builds collision-resistant Redis keys owned by one environment and service. */
public final class RedisKeyBuilder {

    private static final Pattern NAMESPACE_SEGMENT =
            Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    public String build(String environment, String service, String business, String identifier) {
        requireNamespaceSegment(environment, "environment");
        requireNamespaceSegment(service, "service");
        requireNamespaceSegment(business, "business");
        requireIdentifier(identifier);
        return "ygh:" + environment + ':' + service + ':' + business + ':' + identifier;
    }

    public boolean isCanonical(String key) {
        if (key == null || key.length() > 235) {
            return false;
        }
        var segments = key.split(":", -1);
        return segments.length == 5
                && "ygh".equals(segments[0])
                && NAMESPACE_SEGMENT.matcher(segments[1]).matches()
                && NAMESPACE_SEGMENT.matcher(segments[2]).matches()
                && NAMESPACE_SEGMENT.matcher(segments[3]).matches()
                && IDENTIFIER.matcher(segments[4]).matches();
    }

    private static void requireNamespaceSegment(String segment, String name) {
        Objects.requireNonNull(segment, name + " must not be null");
        if (!NAMESPACE_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException(name + " must match "
                    + NAMESPACE_SEGMENT.pattern());
        }
    }

    private static void requireIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier must not be null");
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("identifier must match " + IDENTIFIER.pattern());
        }
    }
}
