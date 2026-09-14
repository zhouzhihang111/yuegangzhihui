package com.yuegang.zhihui.tests.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ScenarioCatalogTest {
    @Test void allRequiredBusinessJourneysAreCatalogued() throws Exception {
        var scenarios = new Properties();
        try (var input = getClass().getResourceAsStream("/scenarios.properties")) { scenarios.load(input); }
        for (String name : List.of("platform", "commerce", "knowledge", "ai", "training")) {
            assertTrue(scenarios.containsKey(name), () -> "missing E2E scenario: " + name);
            assertTrue(scenarios.getProperty(name).split(",").length >= 4, () -> "scenario too shallow: " + name);
        }
    }
}
