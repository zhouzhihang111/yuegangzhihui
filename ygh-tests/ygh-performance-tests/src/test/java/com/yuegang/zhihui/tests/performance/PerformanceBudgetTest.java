package com.yuegang.zhihui.tests.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class PerformanceBudgetTest {
    @Test void enterpriseLatencyBudgetsRemainEnforced() throws Exception {
        var budgets = new Properties();
        try (var input = getClass().getResourceAsStream("/performance-budgets.properties")) { budgets.load(input); }
        assertEquals(500, Integer.parseInt(budgets.getProperty("query.p95.ms")));
        assertEquals(1000, Integer.parseInt(budgets.getProperty("write.p95.ms")));
        assertEquals(1000, Integer.parseInt(budgets.getProperty("search.p95.ms")));
        assertTrue(Double.parseDouble(budgets.getProperty("error.rate.max")) <= 0.01);
    }
}
