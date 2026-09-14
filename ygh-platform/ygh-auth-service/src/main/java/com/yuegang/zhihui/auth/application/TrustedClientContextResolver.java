package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.InternalRequestSignature;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Objects;

public final class TrustedClientContextResolver {
    static final String CLIENT_IP = "X-YGH-Client-IP";
    static final String TIMESTAMP = "X-YGH-Client-IP-Timestamp";
    static final String SIGNATURE = "X-YGH-Client-IP-Signature";
    private final InternalRequestSignature signatures;
    private final boolean requireSignature;

    public TrustedClientContextResolver(InternalRequestSignature signatures) {
        this.signatures = Objects.requireNonNull(signatures, "signatures must not be null");
        this.requireSignature = true;
    }

    private TrustedClientContextResolver() {
        this.signatures = null;
        this.requireSignature = false;
    }

    public static TrustedClientContextResolver directForTests() {
        return new TrustedClientContextResolver();
    }

    public LoginSecurityContext resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String traceId = TraceIdResolver.resolve(request);
        if (!requireSignature) return new LoginSecurityContext(parseIp(request.getRemoteAddr()), traceId);
        String clientIp = request.getHeader(CLIENT_IP);
        String requestId = request.getHeader(TraceIdResolver.REQUEST_ID_HEADER);
        String timestampValue = request.getHeader(TIMESTAMP);
        String signature = request.getHeader(SIGNATURE);
        try {
            Instant timestamp = Instant.ofEpochMilli(Long.parseLong(timestampValue));
            var metadata = new InternalRequestSignature.Metadata(
                    clientIp, traceId, requestId, request.getMethod(), request.getRequestURI(), timestamp);
            if (!signatures.verify(metadata, signature)) throw unauthenticated();
            return new LoginSecurityContext(parseIp(clientIp), traceId);
        } catch (RuntimeException malformed) {
            if (malformed instanceof BusinessException business) throw business;
            throw unauthenticated();
        }
    }

    private static InetAddress parseIp(String value) {
        if (value == null || value.isBlank()) throw unauthenticated();
        if (value.indexOf(':') < 0 && !validIpv4(value)) throw unauthenticated();
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException invalid) {
            throw unauthenticated();
        }
    }

    private static boolean validIpv4(String value) {
        String[] segments = value.split("\\.", -1);
        if (segments.length != 4) return false;
        for (String segment : segments) {
            if (segment.isEmpty() || segment.length() > 3 || !segment.chars().allMatch(Character::isDigit)) return false;
            int number = Integer.parseInt(segment);
            if (number > 255 || (segment.length() > 1 && segment.charAt(0) == '0')) return false;
        }
        return true;
    }

    private static BusinessException unauthenticated() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED);
    }
}
