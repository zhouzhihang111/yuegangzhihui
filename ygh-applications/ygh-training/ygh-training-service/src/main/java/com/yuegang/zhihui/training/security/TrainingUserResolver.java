package com.yuegang.zhihui.training.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

public final class TrainingUserResolver {
    private final InternalUserContextSignature signatures;

    public TrainingUserResolver(byte[] key) {
        signatures = new InternalUserContextSignature(key, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    public long resolve(HttpServletRequest request) { return context(request).userId(); }

    public TrainingUserContext context(HttpServletRequest request) {
        try {
            String user = header(request, "X-YGH-User-Id");
            List<String> roles = values(request.getHeader("X-YGH-Roles"));
            List<String> permissions = values(request.getHeader("X-YGH-Permissions"));
            var metadata = new InternalUserContextSignature.Metadata(user, roles, permissions,
                    header(request, "X-Trace-Id"), header(request, "X-Request-Id"), request.getMethod(),
                    request.getRequestURI(), Instant.ofEpochMilli(Long.parseLong(
                            header(request, "X-YGH-User-Context-Timestamp"))));
            if (!signatures.verify(metadata, header(request, "X-YGH-User-Context-Signature"))) throw failure();
            return new TrainingUserContext(Long.parseLong(user), new LinkedHashSet<>(roles),
                    new LinkedHashSet<>(permissions));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure();
        }
    }

    public long requirePermission(HttpServletRequest request, String permission) {
        TrainingUserContext context = context(request);
        if (!context.permissions().contains(permission) && !context.roles().contains("ADMIN")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return context.userId();
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) throw failure();
        return value;
    }
    private static List<String> values(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }
    private static BusinessException failure() { return new BusinessException(ErrorCode.UNAUTHENTICATED); }
}
