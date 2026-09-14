package com.yuegang.zhihui.tests.contract;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryContractTest {
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

    @Test void everyBusinessDomainPublishesAnApiModule() {
        for (String domain : List.of("user", "system", "product", "inventory", "order", "wallet",
                "knowledge", "search", "ai", "training", "notification", "admin")) {
            Path api = ROOT.resolve("ygh-applications/ygh-" + domain + "/ygh-" + domain + "-api/pom.xml");
            assertTrue(Files.isRegularFile(api), () -> "missing API contract module: " + api);
        }
    }

    @Test void versionedCollectionsAndOpenApiExportAreTracked() {
        assertTrue(Files.isDirectory(ROOT.resolve("spec/bruno")));
        assertTrue(Files.isRegularFile(ROOT.resolve("spec/openapi/gateway-service-v1.json")));
        assertTrue(Files.isRegularFile(ROOT.resolve("spec/backend-delivery.md")));
    }
}
