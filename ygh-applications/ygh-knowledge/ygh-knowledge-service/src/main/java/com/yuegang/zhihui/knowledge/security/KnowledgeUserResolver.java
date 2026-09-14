package com.yuegang.zhihui.knowledge.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class KnowledgeUserResolver {
    private final InternalUserContextSignature signatures;

    public KnowledgeUserResolver(byte[] key) {
        signatures = new InternalUserContextSignature(key, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    public long resolve(HttpServletRequest request, boolean administrator) {
        KnowledgeUserContext context = resolveContext(request);
        if (administrator && !context.administrator()) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return context.userId();
    }

    public KnowledgeUserContext resolveContext(HttpServletRequest request) {
        if (anonymous(request)) return new KnowledgeUserContext(0, Set.of(), Set.of(), Set.of("PUBLIC"));
        try {
            String user = header(request, "X-YGH-User-Id");
            List<String> roleValues = values(request.getHeader("X-YGH-Roles"));
            List<String> permissionValues = values(request.getHeader("X-YGH-Permissions"));
            Set<String> roles = new LinkedHashSet<>(roleValues);
            Set<String> permissions = new LinkedHashSet<>(permissionValues);
            var metadata = new InternalUserContextSignature.Metadata(
                    user, roleValues, permissionValues, header(request, "X-Trace-Id"),
                    header(request, "X-Request-Id"), request.getMethod(), request.getRequestURI(),
                    Instant.ofEpochMilli(Long.parseLong(header(request, "X-YGH-User-Context-Timestamp"))));
            if (!signatures.verify(metadata, header(request, "X-YGH-User-Context-Signature"))) throw failure();
            var visibilities = new LinkedHashSet<String>();
            visibilities.add("PUBLIC");
            if (roles.contains("EMPLOYEE") || roles.contains("ADMIN")
                    || permissions.contains("knowledge:internal:read")) visibilities.add("INTERNAL");
            if (roles.contains("ADMIN") || permissions.contains("knowledge:confidential:read")) {
                visibilities.add("CONFIDENTIAL");
            }
            return new KnowledgeUserContext(Long.parseLong(user), roles, permissions, visibilities);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure();
        }
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) throw failure();
        return value;
    }
    private static boolean anonymous(HttpServletRequest request) {
        String user = request.getHeader("X-YGH-User-Id");
        if (user != null && !user.isBlank()) return false;
        for (String name : List.of("X-YGH-Roles", "X-YGH-Permissions", "X-YGH-User-Context-Timestamp",
                "X-YGH-User-Context-Signature")) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) throw failure();
        }
        return true;
    }
    private static List<String> values(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }
    private static BusinessException failure() { return new BusinessException(ErrorCode.PERMISSION_DENIED); }
}
