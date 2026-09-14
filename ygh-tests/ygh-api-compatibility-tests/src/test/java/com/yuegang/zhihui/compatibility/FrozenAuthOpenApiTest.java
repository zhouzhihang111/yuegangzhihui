package com.yuegang.zhihui.compatibility;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FrozenAuthOpenApiTest {
    private static final Set<String> PATHS = Set.of(
            "/.well-known/jwks.json", "/api/v1/auth/captcha", "/api/v1/auth/register",
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm",
            "/api/v1/auth/password", "/api/v1/auth/admin/users",
            "/api/v1/auth/admin/users/{userId}/status");

    @Test void frozenContractIsEnvironmentNeutralAndComplete() throws Exception {
        Path root = repositoryRoot();
        String raw = Files.readString(root.resolve("spec/openapi/auth-service-v1.json"));
        var api = JsonMapper.builder().build().readTree(raw);
        assertThat(api.path("openapi").asString()).startsWith("3.");
        assertThat(api.path("info").path("version").asString()).isEqualTo("v1");
        assertThat(api.path("servers").get(0).path("url").asString()).isEqualTo("/");
        assertThat(raw).doesNotContain("127.0.0.1", "localhost", "18081");
        assertThat(api.path("paths").propertyNames()).containsExactlyInAnyOrderElementsOf(PATHS);
        assertThat(api.path("paths").path("/api/v1/auth/register").path("post").path("responses").has("201")).isTrue();
        assertThat(api.path("paths").path("/api/v1/auth/password-reset/request").path("post").path("responses").has("202")).isTrue();
        var logout = api.path("paths").path("/api/v1/auth/logout").path("post");
        assertThat(logout.path("security").toString()).contains("bearerAuth");
        for (String status : Set.of("400", "401", "409", "429", "503", "500")) {
            assertThat(logout.path("responses").has(status)).as("logout response %s", status).isTrue();
        }
        assertThat(api.path("components").path("schemas").path("RegisterRequest")
                .path("properties").path("password").path("writeOnly").asBoolean()).isTrue();
        assertThat(api.path("components").path("schemas").path("LogoutRequest")
                .path("properties").path("refreshToken").path("writeOnly").asBoolean()).isTrue();
    }

    private static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("spec/backend-delivery.md"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("repository root cannot be located");
    }
}
