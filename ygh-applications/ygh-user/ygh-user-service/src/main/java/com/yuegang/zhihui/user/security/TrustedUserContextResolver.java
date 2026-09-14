package com.yuegang.zhihui.user.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;

public final class TrustedUserContextResolver {
    private final InternalUserContextSignature signatures;
    public TrustedUserContextResolver(byte[] secret, Clock clock) {
        this.signatures = new InternalUserContextSignature(secret, clock, Duration.ofSeconds(30));
    }
    public CurrentUserPrincipal resolve(HttpServletRequest request) {
        try {
            String userId = header(request, "X-YGH-User-Id");
            List<String> roles = values(request.getHeader("X-YGH-Roles"));
            List<String> permissions = values(request.getHeader("X-YGH-Permissions"));
            Instant timestamp = Instant.ofEpochMilli(Long.parseLong(header(request, "X-YGH-User-Context-Timestamp")));
            var metadata = new InternalUserContextSignature.Metadata(userId, roles, permissions,
                    header(request, "X-Trace-Id"), header(request, "X-Request-Id"), request.getMethod(),
                    request.getRequestURI(), timestamp);
            if (!signatures.verify(metadata, header(request, "X-YGH-User-Context-Signature"))) throw unauthenticated();
            long numericId = Long.parseLong(userId);
            if (numericId <= 0) throw unauthenticated();
            return new CurrentUserPrincipal(userId, new LinkedHashSet<>(roles), new LinkedHashSet<>(permissions));
        } catch (BusinessException expected) { throw expected; }
        catch (RuntimeException malformed) { throw unauthenticated(); }
    }
    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) throw unauthenticated();
        return value;
    }
    private static List<String> values(String encoded) {
        return encoded == null || encoded.isBlank() ? List.of() : List.of(encoded.split(",", -1));
    }
    private static BusinessException unauthenticated() { return new BusinessException(ErrorCode.UNAUTHENTICATED); }
}
