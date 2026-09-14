package com.yuegang.zhihui.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Shared OpenAPI components for every Servlet business service. */
@AutoConfiguration
@ConditionalOnClass({OpenAPI.class, OpenApiCustomizer.class})
public class YghOpenApiAutoConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String REQUEST_ID_PARAMETER = "X-Request-Id";

    @Bean
    @ConditionalOnMissingBean(name = "yghOpenApiCustomizer")
    OpenApiCustomizer yghOpenApiCustomizer() {
        return this::customize;
    }

    private void customize(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }

        components.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT"));

        components.addSchemas("ApiResponse", apiResponseSchema());
        components.addSchemas("FieldValidationError", fieldValidationErrorSchema());
        components.addSchemas("ValidationErrorResponse", validationErrorResponseSchema());

        addErrorResponse(components, "ValidationError", "参数校验失败", "ValidationErrorResponse");
        addErrorResponse(components, "Unauthorized", "未登录或登录已失效", "ApiResponse");
        addErrorResponse(components, "Forbidden", "无权执行该操作", "ApiResponse");
        addErrorResponse(components, "NotFound", "资源不存在", "ApiResponse");
        addErrorResponse(components, "Conflict", "业务状态冲突", "ApiResponse");
        addErrorResponse(components, "RateLimited", "请求过于频繁", "ApiResponse");
        addErrorResponse(components, "DependencyUnavailable", "依赖服务暂不可用", "ApiResponse");
        addErrorResponse(components, "InternalError", "系统内部错误", "ApiResponse");
        components.getResponses().get("RateLimited").addHeaderObject(
                "Retry-After",
                new Header()
                        .description("客户端重试前至少等待的秒数")
                        .schema(new StringSchema().pattern("[1-9][0-9]*")));

        components.addParameters(REQUEST_ID_PARAMETER, new Parameter()
                .name(TraceIdResolver.REQUEST_ID_HEADER)
                .in("header")
                .required(false)
                .description("客户端请求标识；缺失或不安全时由服务生成")
                .schema(new StringSchema()
                        .pattern("[A-Za-z0-9._-]{1,128}")
                        .maxLength(128)));
    }

    private Schema<?> apiResponseSchema() {
        Schema<?> schema = new ObjectSchema()
                .addProperty("code", new StringSchema().description("稳定业务错误码"))
                .addProperty("message", new StringSchema().description("可安全展示的消息"))
                .addProperty("data", new Schema<>().description("业务数据；失败时通常为空"))
                .addProperty("traceId", new StringSchema().description("链路追踪标识"))
                .addProperty("timestamp", new StringSchema().format("date-time"));
        return require(schema, "code", "message", "data", "traceId", "timestamp");
    }

    private Schema<?> fieldValidationErrorSchema() {
        Schema<?> schema = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("rejectedValue", new Schema<>()
                        .description("始终为空，避免敏感输入泄漏"));
        return require(schema, "field", "message", "rejectedValue");
    }

    private Schema<?> validationErrorResponseSchema() {
        Schema<?> schema = new ObjectSchema()
                .addProperty("code", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("data", new ArraySchema().items(
                        new Schema<>().$ref("#/components/schemas/FieldValidationError")))
                .addProperty("traceId", new StringSchema())
                .addProperty("timestamp", new StringSchema().format("date-time"));
        return require(schema, "code", "message", "data", "traceId", "timestamp");
    }

    private void addErrorResponse(
            Components components,
            String name,
            String description,
            String schemaName
    ) {
        var mediaType = new io.swagger.v3.oas.models.media.MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/" + schemaName));
        components.addResponses(name, new ApiResponse()
                .description(description)
                .content(new io.swagger.v3.oas.models.media.Content()
                        .addMediaType("application/json", mediaType)));
    }

    private Schema<?> require(Schema<?> schema, String... propertyNames) {
        for (String propertyName : propertyNames) {
            schema.addRequiredItem(propertyName);
        }
        return schema;
    }
}
