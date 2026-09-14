package com.yuegang.zhihui.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.order.api.AddressSnapshot;
import com.yuegang.zhihui.order.api.CreateOrderRequest;
import com.yuegang.zhihui.order.api.OrderItemCommand;
import com.yuegang.zhihui.order.infrastructure.InventoryClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {
    @Test
    void rebuildsTrustedSnapshotFromPublishedProductAndRejectsUnavailableProduct() throws Exception {
        var published = new AtomicBoolean(true);
        var inventoryAvailable = new AtomicBoolean(true);
        var inventoryExists = new AtomicBoolean(true);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/products/1001", exchange -> {
            String status = published.get() ? "PUBLISHED" : "OFF_SHELF";
            String body = """
                    {"code":"00000","message":"成功","data":{"spuId":"10","skuId":"1001","categoryId":"20","brandId":null,"name":"服务端商品","skuCode":"SERVER-SKU","price":12.50,"currency":"CNY","status":"%s","images":[],"traceabilityCode":"TRACE","version":1},"traceId":"trace","timestamp":"2026-07-12T08:00:00Z"}
                    """.formatted(status);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/internal/v1/inventory/1001", exchange -> {
            if (!inventoryExists.get()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            long available = inventoryAvailable.get() ? 10 : 1;
            String body = """
                    {"code":"00000","message":"成功","data":{"skuId":"1001","available":%d,"locked":0,"sold":0,"version":1},"traceId":"trace","timestamp":"2026-07-12T08:00:00Z"}
                    """.formatted(available);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            var service = new CheckoutService(baseUrl, new InventoryClient(baseUrl,
                    "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
            var request = new CreateOrderRequest("checkout-1",
                    List.of(new OrderItemCommand("1001", "CLIENT", "客户端名称", java.math.BigDecimal.ONE, 2)),
                    new AddressSnapshot("张三", "13800138000", "CN", "440000", "广东省", "广州市", "天河区", "地址", "510000"), null);
            var preview = service.preview(request);
            assertThat(preview.totalAmount()).isEqualByComparingTo("25.00");
            assertThat(preview.items()).singleElement().satisfies(item -> {
                assertThat(item.skuCode()).isEqualTo("SERVER-SKU");
                assertThat(item.productName()).isEqualTo("服务端商品");
            });
            assertThat(service.trusted(request).items()).isEqualTo(preview.items());
            published.set(false);
            assertThatThrownBy(() -> service.preview(request)).isInstanceOf(BusinessException.class);
            published.set(true);
            inventoryAvailable.set(false);
            assertThatThrownBy(() -> service.preview(request)).isInstanceOf(BusinessException.class);
            inventoryAvailable.set(true);
            inventoryExists.set(false);
            assertThatThrownBy(() -> service.preview(request)).isInstanceOf(BusinessException.class);
        } finally {
            server.stop(0);
        }
    }
}
