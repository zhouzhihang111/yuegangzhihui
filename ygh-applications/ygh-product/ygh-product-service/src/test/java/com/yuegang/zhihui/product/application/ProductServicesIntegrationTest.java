package com.yuegang.zhihui.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.product.api.ProductStatus;
import com.yuegang.zhihui.product.api.SaveBrandRequest;
import com.yuegang.zhihui.product.api.SaveCategoryRequest;
import com.yuegang.zhihui.product.api.SaveProductBatchRequest;
import com.yuegang.zhihui.product.api.SaveProductRequest;
import com.yuegang.zhihui.product.api.TraceEventRequest;
import com.yuegang.zhihui.product.api.UpdateProductRequest;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductServicesIntegrationTest {
    @Test
    void managesCatalogProductStatusPriceBatchAndTraceability() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var mapper = new ObjectMapper();
            var catalog = new CatalogService(dataSource, mapper);
            var products = new ProductService(dataSource);
            var administration = new ProductAdministrationService(dataSource, products, mapper);

            var root = catalog.createCategory(new SaveCategoryRequest(null, "FOOD", "食品", 1));
            var snacks = catalog.createCategory(new SaveCategoryRequest(root.id(), "SNACK", "跨境零食", 2));
            var brand = catalog.createBrand(new SaveBrandRequest("LNZ", "岭南滋味", "https://img/logo.png"));
            assertThat(catalog.categories(true)).extracting(c -> c.code()).contains("YGH_MALL", "HK_FRESH", "LINGNAN_SPECIALTY", "CROSS_BORDER_SNACK", "FOOD", "SNACK");
            assertThat(catalog.categories(false)).hasSize(6);
            assertThat(catalog.brands(true)).containsExactly(brand);
            assertThat(catalog.brands(false)).hasSize(1);

            var created = products.create(new SaveProductRequest(snacks.id(), brand.id(), "荔枝曲奇", "SKU-LYCHEE",
                    new BigDecimal("29.90"), "CNY", List.of("https://img/1.png"), "TRACE-001", 0, Map.of("净含量", "200g")));
            assertThat(created.status()).isEqualTo(ProductStatus.DRAFT);
            assertThat(created.specifications()).containsEntry("净含量", "200g");
            assertThat(products.list(snacks.id(), "荔枝", 1000, false)).containsExactly(created);
            assertThat(products.list(null, null, 0, true)).isEmpty();
            assertBusinessError(() -> products.get(created.skuId(), true), ErrorCode.RESOURCE_NOT_FOUND);

            var published = products.changeStatus(created.skuId(), ProductStatus.PUBLISHED, created.version());
            assertThat(products.list(null, "SKU-LYCHEE", 10, true)).containsExactly(published);
            assertBusinessError(() -> products.changeStatus(created.skuId(), ProductStatus.OFF_SHELF, created.version()), ErrorCode.BUSINESS_CONFLICT);

            var updated = administration.update(created.skuId(), new UpdateProductRequest(snacks.id(), "", "荔枝曲奇礼盒",
                    "岭南特产", new BigDecimal("35.50"), "CNY", List.of("https://img/2.png", "https://img/3.png"),
                    "TRACE-002", published.version(), Map.of("净含量", "400g", "包装", "礼盒")));
            assertThat(updated.brandId()).isNull();
            assertThat(updated.images()).containsExactly("https://img/2.png", "https://img/3.png");
            assertThat(updated.price()).isEqualByComparingTo("35.50");
            assertThat(updated.specifications()).containsEntry("包装", "礼盒");
            assertBusinessError(() -> administration.update(created.skuId(), new UpdateProductRequest(snacks.id(), null,
                    "冲突", null, BigDecimal.ONE, "CNY", List.of(), null, 999)), ErrorCode.BUSINESS_CONFLICT);

            var searchRequests = new CopyOnWriteArrayList<String>();
            var failProductSearch = new AtomicBoolean(false);
            HttpServer searchServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            searchServer.createContext("/internal/v1/search/index", exchange -> {
                searchRequests.add("INDEX:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            searchServer.createContext("/internal/v1/search/delete-document", exchange -> {
                searchRequests.add("DELETE:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            });
            searchServer.createContext("/internal/v1/search/products", exchange -> {
                if (failProductSearch.get()) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
                String response = """
                        {"code":"00000","message":"成功","data":[{"skuId":"%s","score":3.0},{"skuId":"invalid"}],"traceId":"trace","timestamp":"2026-07-13T08:00:00Z"}
                        """.formatted(created.skuId());
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            searchServer.start();
            String searchBase = "http://127.0.0.1:" + searchServer.getAddress().getPort();
            byte[] searchSecret = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
            try {
                var dispatcher = new ProductSearchDispatcher(new JdbcTemplate(dataSource), searchBase, searchSecret);
                dispatcher.dispatch();
                assertThat(searchRequests).anySatisfy(request -> {
                    assertThat(request).startsWith("INDEX:");
                    assertThat(request).contains("product:" + created.skuId(), "product-active", "荔枝曲奇礼盒");
                });
                var searchedProducts = new ProductService(dataSource, new ProductSearchGateway(searchBase, searchSecret));
                assertThat(searchedProducts.list(null, "荔枝", 10, true)).containsExactly(updated);
                failProductSearch.set(true);
                assertThat(searchedProducts.list(null, "荔枝", 10, true)).containsExactly(updated);

                var offShelf = products.changeStatus(created.skuId(), ProductStatus.OFF_SHELF, updated.version());
                assertThat(offShelf.status()).isEqualTo(ProductStatus.OFF_SHELF);
                dispatcher.dispatch();
                assertThat(searchRequests).anySatisfy(request -> {
                    assertThat(request).startsWith("DELETE:");
                    assertThat(request).contains("product:" + created.skuId(), "product-active");
                });
            } finally {
                searchServer.stop(0);
            }

            var batch = administration.batch(created.skuId(), new SaveProductBatchRequest("B202607", "广东",
                    "https://proof/1", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 7, 1), "全链路溯源"));
            assertThat(administration.batches(created.skuId())).containsExactly(batch);
            assertBusinessError(() -> administration.batch(created.skuId(), new SaveProductBatchRequest("BAD", null, null,
                    LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), null)), ErrorCode.VALIDATION_ERROR);

            OffsetDateTime occurredAt = OffsetDateTime.of(2026, 7, 2, 10, 30, 0, 0, ZoneOffset.UTC);
            var trace = catalog.addTrace(created.skuId(), new TraceEventRequest("CUSTOMS_CLEARED", "广州南沙", occurredAt,
                    Map.of("declarationNo", "D001")));
            assertThat(catalog.trace(created.skuId())).containsExactly(trace);
            assertBusinessError(() -> catalog.trace("0"), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> products.get("invalid", false), ErrorCode.VALIDATION_ERROR);
        }
    }

    private static void assertBusinessError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(expected));
    }
}
