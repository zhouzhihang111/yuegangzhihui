package com.yuegang.zhihui.tests.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecurityDeliveryTest {
    private static final Path ROOT = locateReactorRoot();

    private static Path locateReactorRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !isReactorRoot(current)) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate Maven reactor root from user.dir");
        }
        return current;
    }

    private static boolean isReactorRoot(Path path) {
        return Files.isRegularFile(path.resolve("pom.xml"))
                && Files.isDirectory(path.resolve("ygh-applications"))
                && Files.isDirectory(path.resolve("ygh-tests"));
    }

    @Test void repositoryDefinesSecretAndDependencyGates() {
        assertTrue(Files.isRegularFile(ROOT.resolve(".github/dependabot.yml")));
        assertTrue(Files.isRegularFile(ROOT.resolve(".github/workflows/backend-quality.yml")));
    }

    @Test void deliveryRepositoryContainsNoExecutableAutomationScripts() throws IOException {
        Set<String> forbiddenExtensions = Set.of(".ps1", ".sh", ".bash", ".bat", ".cmd", ".py");
        try (var files = Files.walk(ROOT)) {
            var forbidden = files.filter(Files::isRegularFile)
                    .filter(path -> !isGeneratedOrInternal(path))
                    .filter(path -> path.getFileName().toString().equals("mvnw")
                            || forbiddenExtensions.stream().anyMatch(
                                    extension -> path.getFileName().toString().toLowerCase().endsWith(extension)))
                    .map(ROOT::relativize)
                    .toList();
            assertTrue(forbidden.isEmpty(), () -> "delivery contains executable scripts: " + forbidden);
        }
        assertTrue(Files.notExists(ROOT.resolve("ygh-deploy/scripts")));
        assertTrue(Files.notExists(ROOT.resolve("ygh-deploy/constrained-dev/scripts")));
        assertTrue(Files.notExists(ROOT.resolve("ygh-web/scripts")));
    }

    @Test void everyJavaRuntimeDropsRootPrivileges() throws IOException {
        try (var files = Files.walk(ROOT)) {
            var dockerfiles = files.filter(path -> path.getFileName().toString().equals("Dockerfile"))
                    .filter(path -> !path.toString().contains("ygh-web")).toList();
            assertTrue(dockerfiles.size() >= 14);
            for (Path dockerfile : dockerfiles) {
                assertTrue(Files.readString(dockerfile).contains("USER 10001:10001"),
                        () -> "runtime does not drop root: " + dockerfile);
            }
        }
    }

    private static boolean isGeneratedOrInternal(Path path) {
        String normalized = ROOT.relativize(path).toString().replace('\\', '/');
        return normalized.startsWith(".git/") || normalized.startsWith("target/")
                || normalized.contains("/target/") || normalized.startsWith("node_modules/")
                || normalized.contains("/node_modules/") || normalized.startsWith("dist/")
                || normalized.contains("/dist/");
    }
}
