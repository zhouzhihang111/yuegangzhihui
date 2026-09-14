package com.yuegang.zhihui.tests.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationIsolationTest {
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

    @Test void everyStatefulServiceOwnsVersionedFlywayMigrations() throws IOException {
        for (String domain : List.of("system", "user", "product", "inventory", "order", "wallet",
                "knowledge", "search", "ai", "training", "notification")) {
            Path base = domain.equals("user")
                    ? ROOT.resolve("ygh-applications/ygh-user/ygh-user-service/src/main/resources/db/migration")
                    : ROOT.resolve("ygh-applications/ygh-" + domain + "/ygh-" + domain + "-service/src/main/resources/db/migration");
            assertTrue(Files.isDirectory(base), () -> "missing migration directory: " + base);
            try (var files = Files.list(base)) {
                var names = files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList();
                assertFalse(names.isEmpty(), () -> "no migrations in " + base);
                assertTrue(names.stream().allMatch(name -> name.matches("V[0-9]+__[a-z0-9_]+\\.sql")),
                        () -> "invalid Flyway migration name in " + base + ": " + names);
            }
        }
    }
}
