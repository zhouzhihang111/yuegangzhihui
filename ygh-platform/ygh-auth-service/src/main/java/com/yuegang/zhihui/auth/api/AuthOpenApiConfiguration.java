package com.yuegang.zhihui.auth.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.Map;
import java.util.List;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuthOpenApiConfiguration {
    private static final Map<String, String> ERRORS = Map.of(
            "400", "ValidationError", "401", "Unauthorized", "403", "Forbidden", "409", "Conflict",
            "429", "RateLimited", "503", "DependencyUnavailable", "500", "InternalError");

    @Bean
    OpenApiCustomizer authOpenApiCustomizer() {
        return openApi -> {
            openApi.info(new Info().title("YGH Authentication API").version("v1")
                    .description("跨境智汇认证服务；所有外部业务路径固定为 /api/v1/auth/**"));
            openApi.servers(List.of(new Server().url("/").description("Gateway relative base URL")));
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                operation.addParametersItem(new Parameter().$ref("#/components/parameters/X-Request-Id"));
                ERRORS.forEach((status, component) -> operation.getResponses().putIfAbsent(status,
                        new ApiResponse().$ref("#/components/responses/" + component)));
                if ("/api/v1/auth/logout".equals(path) || path.startsWith("/api/v1/auth/admin/")) {
                    operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                }
                if ("/api/v1/auth/register".equals(path)) moveSuccess(operation, "201", "Created");
                if ("/api/v1/auth/password-reset/request".equals(path)) moveSuccess(operation, "202", "Accepted");
            }));
        };
    }

    private static void moveSuccess(io.swagger.v3.oas.models.Operation operation,
            String status, String description) {
        ApiResponse success = operation.getResponses().remove("200");
        if (success == null) success = new ApiResponse();
        success.setDescription(description);
        operation.getResponses().addApiResponse(status, success);
    }
}
