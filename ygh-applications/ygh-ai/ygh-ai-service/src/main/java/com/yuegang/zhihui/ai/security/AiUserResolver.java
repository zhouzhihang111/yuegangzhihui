package com.yuegang.zhihui.ai.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

public final class AiUserResolver {
    private final InternalUserContextSignature signatures;

    public AiUserResolver(byte[] key) {
        signatures = new InternalUserContextSignature(key, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    public long resolve(HttpServletRequest request) {
        return resolveContext(request).userId();
    }

    public AiUserContext resolveContext(HttpServletRequest request) {
        Context context = context(request);
        var visibilities = new LinkedHashSet<String>();
        visibilities.add("PUBLIC");
        if (context.roles.contains("EMPLOYEE") || context.roles.contains("ADMIN")
                || context.permissions.contains("knowledge:internal:read")) {
            visibilities.add("INTERNAL");
        }
        if (context.roles.contains("ADMIN") || context.permissions.contains("knowledge:confidential:read")) {
            visibilities.add("CONFIDENTIAL");
        }
        return new AiUserContext(context.user, visibilities);
    }

    public long require(HttpServletRequest request, String permission) {
        Context context = context(request);
        if (!context.roles.contains("ADMIN") && !context.permissions.contains(permission)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return context.user;
    }

    private Context context(HttpServletRequest request) {
        try {
            String user = header(request, "X-YGH-User-Id");
            List<String> roles = values(request.getHeader("X-YGH-Roles"));
            List<String> permissions = values(request.getHeader("X-YGH-Permissions"));
            var metadata = new InternalUserContextSignature.Metadata(
                    user, roles, permissions, header(request, "X-Trace-Id"), header(request, "X-Request-Id"),
                    request.getMethod(), request.getRequestURI(), Instant.ofEpochMilli(Long.parseLong(
                            header(request, "X-YGH-User-Context-Timestamp"))));
            if (!signatures.verify(metadata, header(request, "X-YGH-User-Context-Signature"))) throw failure();
            return new Context(Long.parseLong(user), roles, permissions);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure();
        }
    }

    private record Context(long user, List<String> roles, List<String> permissions) {}
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
