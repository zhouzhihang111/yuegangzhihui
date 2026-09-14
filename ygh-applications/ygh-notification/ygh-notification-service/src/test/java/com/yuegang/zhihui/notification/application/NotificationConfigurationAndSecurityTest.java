package com.yuegang.zhihui.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import com.yuegang.zhihui.notification.security.NotificationSecurity;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class NotificationConfigurationAndSecurityTest {
    private static final byte[] KEY = "01234567890123456789012345678901".getBytes();

    @Test
    void createsConfigurationBeansAndDispatchesScheduledJob() {
        var configuration = new NotificationConfiguration();
        assertThat(new NotificationMqConfiguration()).isNotNull();
        DataSource dataSource = mock(DataSource.class);
        NotificationService service = mock(NotificationService.class);
        when(service.dispatchPending()).thenReturn(3);

        assertThat(configuration.notificationService(dataSource)).isNotNull();
        assertThat(configuration.notificationQueryService(dataSource)).isNotNull();
        assertThat(configuration.notificationSecurity(
                Base64.getEncoder().encodeToString(KEY))).isNotNull();
        NotificationDispatchJob job = configuration.notificationDispatchJob(service);
        job.dispatch();
    }

    @Test
    void verifiesUserPermissionAdminAndInternalServiceSignatures() {
        NotificationSecurity security = new NotificationSecurity(KEY);
        HttpServletRequest user = signedUser("7", "USER", "notification:compensate");
        assertThat(security.user(user)).isEqualTo(7);
        assertThat(security.require(user, "notification:compensate")).isEqualTo(7);
        assertThatThrownBy(() -> security.require(user, "notification:admin"))
                .isInstanceOf(BusinessException.class);
        assertThat(security.require(signedUser("8", "ADMIN", ""), "anything")).isEqualTo(8);

        HttpServletRequest serviceRequest = signedService();
        security.service(serviceRequest);
        when(serviceRequest.getHeader("X-YGH-Service-Signature")).thenReturn("invalid");
        assertThatThrownBy(() -> security.service(serviceRequest)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> security.user(mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class);
    }

    private static HttpServletRequest signedUser(String user, String roleHeader, String permissionHeader) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String timestamp = Long.toString(System.currentTimeMillis());
        List<String> roles = roleHeader.isBlank() ? List.of() : List.of(roleHeader.split(","));
        List<String> permissions = permissionHeader.isBlank()
                ? List.of() : List.of(permissionHeader.split(","));
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/notifications");
        when(request.getHeader("X-YGH-User-Id")).thenReturn(user);
        when(request.getHeader("X-YGH-Roles")).thenReturn(roleHeader);
        when(request.getHeader("X-YGH-Permissions")).thenReturn(permissionHeader);
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-notification");
        when(request.getHeader("X-Request-Id")).thenReturn("request-notification");
        when(request.getHeader("X-YGH-User-Context-Timestamp")).thenReturn(timestamp);
        var metadata = new InternalUserContextSignature.Metadata(
                user, roles, permissions, "trace-notification", "request-notification", "GET",
                "/api/v1/notifications", Instant.ofEpochMilli(Long.parseLong(timestamp)));
        when(request.getHeader("X-YGH-User-Context-Signature")).thenReturn(
                new InternalUserContextSignature(KEY, Clock.systemUTC(), Duration.ofSeconds(30)).sign(metadata));
        return request;
    }

    private static HttpServletRequest signedService() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Instant now = Instant.now();
        String path = "/internal/v1/notifications";
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn(path);
        when(request.getHeader("X-YGH-Service")).thenReturn("ygh-training-service");
        when(request.getHeader("X-YGH-Service-Timestamp")).thenReturn(Long.toString(now.toEpochMilli()));
        var metadata = new InternalServiceSignature.Metadata(
                "ygh-training-service", "POST", path, now);
        when(request.getHeader("X-YGH-Service-Signature")).thenReturn(
                new InternalServiceSignature(KEY, Clock.systemUTC(), Duration.ofSeconds(30)).sign(metadata));
        return request;
    }
}
