package com.yuegang.zhihui.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FrontendOpenApiContractTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");
    private static final Pattern PATH_LITERAL = Pattern.compile("(/api/v1/[^\\\"'`\\s?]+)");
    private static final Pattern DIRECT_HTTP_CALL = Pattern.compile(
            "(?:useHttp\\(\\)|client)\\.(get|post|put|delete|patch)\\(\\s*[\\\"'`]"
                    + "(/api/v1/[^\\\"'`\\s?]+)[\\\"'`]",
            Pattern.DOTALL);
    private static final Pattern TYPESCRIPT_TEMPLATE = Pattern.compile("\\$\\{[^}]+}");
    private static final Pattern OPENAPI_TEMPLATE = Pattern.compile("\\{[^}]+}");

    @Test
    void everyFrontendApiPathAndDirectMethodExistsInFrozenOpenApi() throws IOException {
        Path root = repositoryRoot();
        Map<String, Set<String>> contracts = loadContracts(root.resolve("spec/openapi"));
        List<String> missingPaths = new ArrayList<>();
        List<String> methodMismatches = new ArrayList<>();
        Set<String> inspectedPaths = new HashSet<>();
        int directCalls = 0;

        for (Path source : frontendSources(root)) {
            String content = Files.readString(source);
            var paths = PATH_LITERAL.matcher(content);
            while (paths.find()) {
                String path = normalizeFrontendPath(paths.group(1));
                if (inspectedPaths.add(path) && !contracts.containsKey(path)) {
                    missingPaths.add(relative(root, source) + " -> " + path);
                }
            }

            var calls = DIRECT_HTTP_CALL.matcher(content);
            while (calls.find()) {
                directCalls++;
                String method = calls.group(1).toLowerCase(Locale.ROOT);
                String path = normalizeFrontendPath(calls.group(2));
                Set<String> allowed = contracts.getOrDefault(path, Set.of());
                if (!allowed.contains(method)) {
                    methodMismatches.add(relative(root, source) + " -> " + method.toUpperCase(Locale.ROOT)
                            + " " + path + " (OpenAPI: " + allowed + ")");
                }
            }
        }

        assertThat(inspectedPaths).as("frontend API path coverage").hasSizeGreaterThan(100);
        assertThat(directCalls).as("direct frontend HTTP calls inspected").isGreaterThan(100);
        assertThat(missingPaths).as("frontend paths missing from frozen OpenAPI").isEmpty();
        assertThat(methodMismatches).as("frontend HTTP methods incompatible with frozen OpenAPI").isEmpty();
    }

    private static Map<String, Set<String>> loadContracts(Path directory) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Set<String>> result = new HashMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("-v1.json")).toList()) {
                JsonNode paths = mapper.readTree(file.toFile()).path("paths");
                paths.fields().forEachRemaining(entry -> {
                    Set<String> methods = result.computeIfAbsent(normalizeOpenApiPath(entry.getKey()), ignored -> new HashSet<>());
                    entry.getValue().fieldNames().forEachRemaining(name -> {
                        String method = name.toLowerCase(Locale.ROOT);
                        if (HTTP_METHODS.contains(method)) {
                            methods.add(method);
                        }
                    });
                });
            }
        }
        return result;
    }

    private static List<Path> frontendSources(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path sourceRoot : List.of(
                root.resolve("ygh-web/apps/ygh-web-mall/src"),
                root.resolve("ygh-web/apps/ygh-web-admin/src"),
                root.resolve("ygh-web/packages/ygh-web-shared/src"))) {
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                result.addAll(files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".ts") || path.toString().endsWith(".vue"))
                        .toList());
            }
        }
        return result;
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("spec/openapi"))
                    && Files.isDirectory(candidate.resolve("ygh-web/apps"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root from " + Path.of("").toAbsolutePath());
    }

    private static String normalizeFrontendPath(String path) {
        return TYPESCRIPT_TEMPLATE.matcher(stripTrailingSlash(path)).replaceAll("{}");
    }

    private static String normalizeOpenApiPath(String path) {
        return OPENAPI_TEMPLATE.matcher(stripTrailingSlash(path)).replaceAll("{}");
    }

    private static String stripTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String relative(Path root, Path source) {
        return root.relativize(source).toString().replace('\\', '/');
    }
}
