package com.yuegang.zhihui.training.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import com.yuegang.zhihui.training.infrastructure.OrganizationTargetClient;
import com.yuegang.zhihui.training.security.TrainingUserResolver;
import com.yuegang.zhihui.training.api.AssignmentView;
import com.yuegang.zhihui.training.api.CreateAssignmentRequest;
import com.yuegang.zhihui.training.api.CreateScopedAssignmentRequest;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class TrainingConfigurationAndSecurityTest {
    private static final byte[] KEY = "01234567890123456789012345678901".getBytes();

    @Test
    void createsAllTrainingBeans() throws Exception {
        var configuration = new TrainingConfiguration();
        var dataSource = mock(DataSource.class);
        var json = new ObjectMapper();
        var assignments = configuration.trainingAssignmentService(dataSource, json);
        var targets = mock(OrganizationTargetClient.class);
        String secret = Base64.getEncoder().encodeToString(KEY);

        assertThat(configuration.trainingProgressService(dataSource)).isNotNull();
        assertThat(configuration.trainingQuizService(dataSource, json)).isNotNull();
        assertThat(configuration.trainingLearningRecordService(dataSource, json)).isNotNull();
        assertThat(configuration.scopedAssignmentService(assignments, targets)).isNotNull();
        assertThat(configuration.organizationTargetClient("http://localhost", secret, json)).isNotNull();
        assertThat(configuration.learningPathService(dataSource)).isNotNull();
        assertThat(configuration.trainingContentService(
                dataSource, json, Files.createTempDirectory("training-config").toString())).isNotNull();
        assertThat(configuration.trainingUserResolver(secret)).isNotNull();
    }

    @Test
    void resolvesSignedUsersAndEnforcesPermissionOrAdminRole() {
        TrainingUserResolver resolver = new TrainingUserResolver(KEY);
        HttpServletRequest permitted = signed("7", "USER", "training:statistics:read");
        assertThat(resolver.resolve(permitted)).isEqualTo(7);
        assertThat(resolver.requirePermission(permitted, "training:statistics:read")).isEqualTo(7);
        assertThatThrownBy(() -> resolver.requirePermission(permitted, "training:course:write"))
                .isInstanceOf(BusinessException.class);
        assertThat(resolver.requirePermission(signed("8", "ADMIN", ""), "anything")).isEqualTo(8);

        HttpServletRequest missing = mock(HttpServletRequest.class);
        assertThatThrownBy(() -> resolver.resolve(missing)).isInstanceOf(BusinessException.class);
        HttpServletRequest tampered = signed("9", "USER", "training:read");
        when(tampered.getHeader("X-YGH-User-Context-Signature")).thenReturn("invalid");
        assertThatThrownBy(() -> resolver.resolve(tampered)).isInstanceOf(BusinessException.class);
    }

    @Test
    void resolvesOrganizationTargetsAndExpandsScopedAssignments() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/organization/targets", exchange -> {
            String response = exchange.getRequestURI().getQuery().contains("id=empty")
                    ? "{\"code\":\"SUCCESS\",\"data\":null}"
                    : "{\"code\":\"SUCCESS\",\"data\":[\"41\",\"42\"]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            var targets = new OrganizationTargetClient(base, KEY, new ObjectMapper());
            assertThat(targets.resolve("DEPARTMENT", "sales")).containsExactly("41", "42");
            assertThat(targets.resolve("DEPARTMENT", "empty")).isEmpty();

            TrainingAssignmentService assignments = mock(TrainingAssignmentService.class);
            when(assignments.assign(eq(9L), any(CreateAssignmentRequest.class)))
                    .thenAnswer(invocation -> {
                        CreateAssignmentRequest request = invocation.getArgument(1);
                        return new AssignmentView("a-" + request.userId(), request.userId(),
                                request.courseId(), "ASSIGNED", request.dueAt());
                    });
            var scoped = new ScopedAssignmentService(assignments, targets);
            var result = scoped.assign(9, new CreateScopedAssignmentRequest(
                    "DEPARTMENT", "sales", null, "100", null));
            assertThat(result.assignedCount()).isEqualTo(2);
            assertThat(result.assignments()).extracting(AssignmentView::userId)
                    .containsExactly("41", "42");
        } finally {
            server.stop(0);
        }
    }

    private static HttpServletRequest signed(String user, String rolesHeader, String permissionsHeader) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String timestamp = Long.toString(System.currentTimeMillis());
        List<String> roles = rolesHeader.isBlank() ? List.of() : List.of(rolesHeader.split(","));
        List<String> permissions = permissionsHeader.isBlank()
                ? List.of() : List.of(permissionsHeader.split(","));
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/training/courses");
        when(request.getHeader("X-YGH-User-Id")).thenReturn(user);
        when(request.getHeader("X-YGH-Roles")).thenReturn(rolesHeader);
        when(request.getHeader("X-YGH-Permissions")).thenReturn(permissionsHeader);
        when(request.getHeader("X-Trace-Id")).thenReturn("trace-training");
        when(request.getHeader("X-Request-Id")).thenReturn("request-training");
        when(request.getHeader("X-YGH-User-Context-Timestamp")).thenReturn(timestamp);
        var metadata = new InternalUserContextSignature.Metadata(
                user, roles, permissions, "trace-training", "request-training", "GET",
                "/api/v1/training/courses", Instant.ofEpochMilli(Long.parseLong(timestamp)));
        when(request.getHeader("X-YGH-User-Context-Signature")).thenReturn(
                new InternalUserContextSignature(KEY, Clock.systemUTC(), Duration.ofSeconds(30)).sign(metadata));
        return request;
    }
}
