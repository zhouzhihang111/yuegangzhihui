package com.yuegang.zhihui.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.admin.security.AdminUserVerifier;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminConfigurationAndSecurityTest {
    private static final byte[] KEY = "01234567890123456789012345678901".getBytes();

    @Test
    void createsAllAdminBeans() {
        var configuration = new AdminConfiguration();
        assertThat(configuration.adminDashboardService("gateway=http://localhost/health")).isNotNull();
        assertThat(configuration.auditQueryService("http://localhost", new ObjectMapper())).isNotNull();
        assertThat(configuration.adminUserVerifier(Base64.getEncoder().encodeToString(KEY))).isNotNull();
    }

    @Test
    void acceptsOnlySignedAdminContext() {
        AdminUserVerifier verifier = new AdminUserVerifier(KEY);
        HttpServletRequest admin = signed("7", "ADMIN");
        verifier.verify(admin);
        assertThatThrownBy(() -> verifier.verify(signed("8", "USER")))
                .isInstanceOf(BusinessException.class);
        when(admin.getHeader("X-YGH-User-Context-Signature")).thenReturn("invalid");
        assertThatThrownBy(() -> verifier.verify(admin)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> verifier.verify(mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class);
    }

    private static HttpServletRequest signed(String user, String role) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String timestamp = Long.toString(System.currentTimeMillis());
        List<String> roles = List.of(role);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/admin/dashboard");
        when(request.getHeader("X-YGH-User-Id")).thenReturn(user);
        when(request.getHeader("X-YGH-Roles")).thenReturn(role);
        when(request.getHeader("X-YGH-Permissions")).thenReturn("");
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-admin");
        when(request.getHeader("X-Request-Id")).thenReturn("request-admin");
        when(request.getHeader("X-YGH-User-Context-Timestamp")).thenReturn(timestamp);
        var metadata = new InternalUserContextSignature.Metadata(
                user, roles, List.of(), "trace-admin", "request-admin", "GET",
                "/api/v1/admin/dashboard", Instant.ofEpochMilli(Long.parseLong(timestamp)));
        when(request.getHeader("X-YGH-User-Context-Signature")).thenReturn(
                new InternalUserContextSignature(KEY, Clock.systemUTC(), Duration.ofSeconds(30)).sign(metadata));
        return request;
    }
}
