package com.yuegang.zhihui.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.inventory.api.InventoryCommand;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryClientTest {
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void signsAllInventoryCommands() throws Exception {
        var paths = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/inventory", exchange -> {
            String service = exchange.getRequestHeaders().getFirst("X-YGH-Service");
            Instant at = Instant.ofEpochMilli(Long.parseLong(exchange.getRequestHeaders().getFirst("X-YGH-Service-Timestamp")));
            String signature = exchange.getRequestHeaders().getFirst("X-YGH-Service-Signature");
            var metadata = new InternalServiceSignature.Metadata(service, exchange.getRequestMethod(), exchange.getRequestURI().getPath(), at);
            assertThat(new InternalServiceSignature(SECRET, Clock.systemUTC(), Duration.ofSeconds(30)).verify(metadata, signature)).isTrue();
            paths.add(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new InventoryClient("http://127.0.0.1:" + server.getAddress().getPort(), SECRET);
            var command = new InventoryCommand("request", "1001", 1, "ORDER", "order");
            client.reserve(command);
            client.confirm(command);
            client.release(command);
            client.returnSold(command);
            assertThat(paths).containsExactlyElementsOf(List.of(
                    "/internal/v1/inventory/reserve", "/internal/v1/inventory/confirm",
                    "/internal/v1/inventory/release", "/internal/v1/inventory/return-sold"));
        } finally {
            server.stop(0);
        }
    }
}
