package com.yuegang.zhihui.inventory.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class InventoryAdminVerifier {
    private final InternalUserContextSignature signatures;

    public InventoryAdminVerifier(@Value("${ygh.internal-request.hmac-base64}") String encoded) {
        byte[] secret = Base64.getDecoder().decode(encoded);
        try { this.signatures = new InternalUserContextSignature(secret, Clock.systemUTC(), Duration.ofSeconds(30)); }
        finally { Arrays.fill(secret, (byte) 0); }
    }

    public void require(HttpServletRequest request) {
        try {
            String user = header(request, "X-YGH-User-Id");
            List<String> roles = values(request.getHeader("X-YGH-Roles"));
            List<String> permissions = values(request.getHeader("X-YGH-Permissions"));
            var metadata = new InternalUserContextSignature.Metadata(user, roles, permissions,
                    header(request, "X-Trace-Id"), header(request, "X-Request-Id"), request.getMethod(),
                    request.getRequestURI(), Instant.ofEpochMilli(Long.parseLong(header(request, "X-YGH-User-Context-Timestamp"))));
            if (!signatures.verify(metadata, header(request, "X-YGH-User-Context-Signature"))) throw unauthenticated();
            if (!roles.contains("ADMIN") && !permissions.contains("inventory:read")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        } catch (BusinessException failure) { throw failure; }
        catch (RuntimeException failure) { throw unauthenticated(); }
    }

    private static String header(HttpServletRequest request, String name) { String value=request.getHeader(name);if(value==null||value.isBlank())throw unauthenticated();return value; }
    private static List<String> values(String value) { return value==null||value.isBlank()?List.of():List.of(value.split(",")); }
    private static BusinessException unauthenticated() { return new BusinessException(ErrorCode.UNAUTHENTICATED); }
}
