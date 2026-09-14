package com.yuegang.zhihui.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@SuppressWarnings({"rawtypes", "unchecked"})
class YghOpenApiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(YghOpenApiAutoConfiguration.class));

    @Test
    void autoConfigurationProvidesOneReusableOpenApiCustomizer() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(YghOpenApiAutoConfiguration.class);
            assertThat(context).hasSingleBean(OpenApiCustomizer.class);
        });
    }

    @Test
    void customizerPublishesBearerSchemasErrorResponsesAndRequestIdWithoutGlobalSecurity() {
        contextRunner.run(context -> {
            var openApi = new OpenAPI();

            context.getBean(OpenApiCustomizer.class).customise(openApi);

            assertBearerJwt(openApi);
            assertSchemas(openApi);
            assertReusableErrorResponses(openApi);
            assertRequestIdParameter(openApi);
            assertThat(openApi.getSecurity()).isNullOrEmpty();
        });
    }

    @Test
    void autoConfigurationIsRegisteredForBootDiscovery() throws IOException {
        var resourceName = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

        try (var input = YghOpenApiAutoConfigurationTest.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            assertThat(input).as(resourceName).isNotNull();
            var registrations = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(registrations)
                    .contains("com.yuegang.zhihui.common.web.YghOpenApiAutoConfiguration");
        }
    }

    private void assertBearerJwt(OpenAPI openApi) {
        var securityScheme = openApi.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(securityScheme).isNotNull();
        assertThat(securityScheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(securityScheme.getScheme()).isEqualTo("bearer");
        assertThat(securityScheme.getBearerFormat()).isEqualTo("JWT");
    }

    private void assertSchemas(OpenAPI openApi) {
        var schemas = openApi.getComponents().getSchemas();
        assertThat(schemas).containsKeys("ApiResponse", "FieldValidationError", "ValidationErrorResponse");
        assertThat(schemas.get("ApiResponse").getProperties())
                .containsKeys("code", "message", "data", "traceId", "timestamp");
        assertThat(schemas.get("ApiResponse").getRequired())
                .containsExactlyInAnyOrder("code", "message", "data", "traceId", "timestamp");
        assertThat(schemas.get("FieldValidationError").getProperties())
                .containsKeys("field", "message", "rejectedValue");
        assertThat(schemas.get("FieldValidationError").getRequired())
                .containsExactlyInAnyOrder("field", "message", "rejectedValue");

        Schema<?> validationResponse = schemas.get("ValidationErrorResponse");
        assertThat(validationResponse.getProperties())
                .containsKeys("code", "message", "data", "traceId", "timestamp");
        assertThat(validationResponse.getRequired())
                .containsExactlyInAnyOrder("code", "message", "data", "traceId", "timestamp");
        assertThat(validationResponse.getProperties().get("data")).isInstanceOf(ArraySchema.class);
        var validationItems = ((ArraySchema) validationResponse.getProperties().get("data")).getItems();
        assertThat(validationItems.get$ref()).isEqualTo("#/components/schemas/FieldValidationError");
    }

    private void assertReusableErrorResponses(OpenAPI openApi) {
        var responses = openApi.getComponents().getResponses();
        var names = List.of(
                "ValidationError",
                "Unauthorized",
                "Forbidden",
                "NotFound",
                "Conflict",
                "RateLimited",
                "DependencyUnavailable",
                "InternalError");
        assertThat(responses).containsKeys(names.toArray(String[]::new));

        names.forEach(name -> {
            var response = responses.get(name);
            assertThat(response.getDescription()).isNotBlank();
            assertThat(response.getContent()).containsKey("application/json");
            var schema = response.getContent().get("application/json").getSchema();
            assertThat(schema).isNotNull();
            var expectedSchema = "ValidationError".equals(name)
                    ? "#/components/schemas/ValidationErrorResponse"
                    : "#/components/schemas/ApiResponse";
            assertThat(schema.get$ref()).isEqualTo(expectedSchema);
        });

        var retryAfter = responses.get("RateLimited").getHeaders().get("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThat(retryAfter.getSchema().getType()).isEqualTo("string");
        assertThat(retryAfter.getSchema().getPattern()).isEqualTo("[1-9][0-9]*");
    }

    private void assertRequestIdParameter(OpenAPI openApi) {
        var requestId = openApi.getComponents().getParameters().get("X-Request-Id");
        assertThat(requestId).isNotNull();
        assertThat(requestId.getName()).isEqualTo("X-Request-Id");
        assertThat(requestId.getIn()).isEqualTo("header");
        assertThat(requestId.getRequired()).isFalse();
        assertThat(requestId.getSchema().getType()).isEqualTo("string");
        assertThat(requestId.getSchema().getPattern()).isEqualTo("[A-Za-z0-9._-]{1,128}");
    }
}
