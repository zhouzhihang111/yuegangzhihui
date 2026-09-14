package com.yuegang.zhihui.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Records safe request metadata without reading query values or request bodies. */
public final class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REDACTED = "[REDACTED]";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final int MAX_USER_AGENT_LENGTH = 256;
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
    private static final Log LOGGER = LogFactory.getLog(RequestLoggingFilter.class);
    private final RequestLogSink sink;

    public RequestLoggingFilter(RequestLogSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink must not be null");
        }
        this.sink = sink;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String traceId = resolveOrCreateTraceId(request);
        String requestId = resolveOrCreateRequestId(request);
        request.setAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE, traceId);
        request.setAttribute(TraceIdResolver.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(TraceIdResolver.TRACE_ID_HEADER, traceId);
        response.setHeader(TraceIdResolver.REQUEST_ID_HEADER, requestId);
        MDC.put("traceId", traceId);
        MDC.put("requestId", requestId);

        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
            int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            publishSafely(new RequestLogEvent(
                    traceId, requestId, request.getMethod(), request.getRequestURI(),
                    status, durationMs, safeHeaders(request)));
            MDC.remove("requestId");
            MDC.remove("traceId");
        }
    }

    private String resolveOrCreateTraceId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE);
        if (attribute instanceof String value && isSafeIdentifier(value)) return value;
        String header = request.getHeader(TraceIdResolver.TRACE_ID_HEADER);
        return isSafeIdentifier(header) ? header : newIdentifier();
    }

    private String resolveOrCreateRequestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(TraceIdResolver.REQUEST_ID_ATTRIBUTE);
        if (attribute instanceof String value && !value.isBlank()) {
            return isSafeIdentifier(value) ? value : newIdentifier();
        }
        String header = request.getHeader(TraceIdResolver.REQUEST_ID_HEADER);
        return isSafeIdentifier(header) ? header : newIdentifier();
    }

    private Map<String, String> safeHeaders(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", sanitizeUserAgent(userAgent));
        return headers;
    }

    private String sanitizeUserAgent(String userAgent) {
        String withoutControlCharacters = CONTROL_CHARACTERS.matcher(userAgent).replaceAll("");
        String normalized = withoutControlCharacters.toLowerCase(Locale.ROOT);
        if (normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("cookie")
                || normalized.contains("authorization")) {
            return REDACTED;
        }
        return withoutControlCharacters.substring(
                0, Math.min(withoutControlCharacters.length(), MAX_USER_AGENT_LENGTH));
    }

    private boolean isSafeIdentifier(String value) {
        return value != null
                && !value.isBlank()
                && !"unavailable".equals(value)
                && value.length() <= MAX_CORRELATION_ID_LENGTH
                && SAFE_CORRELATION_ID.matcher(value).matches();
    }

    private void publishSafely(RequestLogEvent event) {
        try {
            sink.accept(event);
        } catch (RuntimeException exception) {
            LOGGER.warn("Request log sink failed, type=" + exception.getClass().getName());
        }
    }

    private String newIdentifier() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
