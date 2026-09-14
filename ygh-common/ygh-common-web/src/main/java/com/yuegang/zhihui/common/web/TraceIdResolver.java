package com.yuegang.zhihui.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the request trace identifier without creating a second tracing system.
 */
public final class TraceIdResolver {

    public static final String TRACE_ID_ATTRIBUTE = "traceId";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String UNAVAILABLE = "unavailable";

    private TraceIdResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        var attribute = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String traceId && !traceId.isBlank()) {
            return traceId;
        }

        var requestId = request.getHeader(REQUEST_ID_HEADER);
        return requestId == null || requestId.isBlank() ? UNAVAILABLE : requestId;
    }
}
