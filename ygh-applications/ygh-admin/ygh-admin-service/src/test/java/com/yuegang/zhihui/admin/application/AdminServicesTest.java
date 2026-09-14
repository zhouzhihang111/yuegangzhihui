package com.yuegang.zhihui.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yuegang.zhihui.common.core.BusinessException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminServicesTest {
    private HttpServer server;
    private String base;
    private final AtomicReference<String> lokiQuery = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        base = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/health-up", exchange -> reply(exchange, "{\"status\":\"UP\"}", 200));
        server.createContext("/health-empty", exchange -> reply(exchange, "{}", 200));
        server.createContext("/loki/api/v1/query_range", exchange -> {
            lokiQuery.set(exchange.getRequestURI().getQuery());
            reply(exchange,
                "{\"data\":{\"result\":[{\"stream\":{\"job\":\"ygh-order-service\"},\"values\":["
                        + "[\"1783872000000000000\",\"business_mutation userId=7 method=POST path=/api/v1/orders status=201 traceId=t1\"],"
                        + "[\"1783871000000000000\",\"business_mutation userId=8 method=DELETE path=/api/v1/orders/1 status=500 traceId=t2\"]]}]}}",
                200);
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void aggregatesHealthWithoutCrossDatabaseQueries() {
        var dashboard = new AdminDashboardService(
                "gateway=" + base + "/health-up,unknown=" + base + "/health-empty,down=http://localhost:1/down")
                .dashboard();
        assertThat(dashboard.summary().totalServices()).isEqualTo(3);
        assertThat(dashboard.summary().healthyServices()).isEqualTo(1);
        assertThat(dashboard.services()).extracting(x -> x.status())
                .containsExactlyInAnyOrder("UP", "UNKNOWN", "DOWN");
        assertThat(dashboard.pending()).hasSize(2);
        assertThat(new AdminDashboardService("invalid-entry").dashboard().services()).isEmpty();
    }

    @Test
    void queriesFiltersAndValidatesLokiAuditRecords() {
        var service = new AuditQueryService(base, new ObjectMapper());
        OffsetDateTime end = OffsetDateTime.parse("2026-07-13T00:00:00Z");
        var records = service.query("7", "order", "POST", "SUCCESS", end.minusHours(2), end, 1000);
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.userId()).isEqualTo("7");
            assertThat(record.action()).isEqualTo("POST /api/v1/orders");
            assertThat(record.status()).isEqualTo(201);
        });
        assertThat(lokiQuery.get()).startsWith("query={job=~\".+\"}");
        assertThat(service.query(null, null, null, null, null, null, 0)).hasSize(1);
        assertThatThrownBy(() -> service.query(null, null, null, null, end, end, 10))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.query("bad value!", null, null, null, end.minusHours(1), end, 10))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.query(null, null, null, null, end.minusDays(32), end, 10))
                .isInstanceOf(BusinessException.class);
        assertThat(new AuditQueryService("http://localhost:1", new ObjectMapper())
                .query(null, null, null, null, end.minusHours(1), end, 10)).isEmpty();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, String body, int status)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
