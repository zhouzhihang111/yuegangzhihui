package com.yuegang.zhihui.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.inventory.api.InventoryCommand;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class InventoryServicesIntegrationTest {
    @Test
    void supportsIdempotentLifecycleExpiryReturnReconciliationAndConcurrentNoOversell() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var inventory = new InventoryService(dataSource);
            var returns = new InventoryReturnService(dataSource, inventory);
            var maintenance = new InventoryMaintenanceService(dataSource);

            assertBusinessError(() -> inventory.get("1"), ErrorCode.RESOURCE_NOT_FOUND);
            var adjustment = command("adjust-1", "1", 20, "ADMIN", "seed");
            assertThat(inventory.adjust(adjustment).available()).isEqualTo(20);
            assertThat(inventory.adjust(adjustment).available()).isEqualTo(20);

            var sale = command("sale-1", "1", 4, "ORDER", "order-1");
            assertThat(inventory.reserve(sale).locked()).isEqualTo(4);
            assertThat(inventory.reserve(sale).locked()).isEqualTo(4);
            assertThat(inventory.confirm(sale).sold()).isEqualTo(4);
            assertThat(inventory.confirm(sale).sold()).isEqualTo(4);
            assertThat(returns.returnSold(sale).sold()).isZero();
            assertThat(returns.returnSold(sale).available()).isEqualTo(20);

            var release = command("release-1", "1", 3, "ORDER", "order-2");
            inventory.reserve(release);
            assertThat(inventory.release(release).available()).isEqualTo(20);
            assertThat(inventory.release(release).available()).isEqualTo(20);
            assertBusinessError(() -> inventory.release(command("missing", "1", 1, "ORDER", "none")), ErrorCode.BUSINESS_CONFLICT);

            var expired = command("expired-1", "1", 2, "ORDER", "order-3");
            inventory.reserve(expired);
            try (var connection = DriverManager.getConnection(mysql.jdbcUrl(), mysql.username(), mysql.credential())) {
                connection.createStatement().executeUpdate("UPDATE inventory_reservation SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE request_id='expired-1'");
            }
            assertThat(maintenance.releaseExpired()).isEqualTo(1);
            assertThat(maintenance.releaseExpired()).isZero();
            assertThat(maintenance.reconcile()).singleElement().satisfies(view -> assertThat(view.status()).isEqualTo("MATCHED"));

            inventory.adjust(command("adjust-2", "2", 10, "ADMIN", "seed-2"));
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 20; i++) {
                int sequence = i;
                tasks.add(() -> {
                    try {
                        inventory.reserve(command("concurrent-" + sequence, "2", 1, "ORDER", "order-" + sequence));
                        return true;
                    } catch (BusinessException error) {
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT);
                        return false;
                    }
                });
            }
            try (var executor = Executors.newFixedThreadPool(8)) {
                long successes = executor.invokeAll(tasks).stream().filter(future -> {
                    try { return future.get(); } catch (Exception error) { throw new AssertionError(error); }
                }).count();
                assertThat(successes).isEqualTo(10);
            }
            var stock = inventory.get("2");
            assertThat(stock.available()).isZero();
            assertThat(stock.locked()).isEqualTo(10);
            assertBusinessError(() -> inventory.reserve(command("overflow", "2", 1, "ORDER", "overflow")), ErrorCode.BUSINESS_CONFLICT);
            assertBusinessError(() -> inventory.get("0"), ErrorCode.VALIDATION_ERROR);
            assertBusinessError(() -> returns.returnSold(command("bad", "invalid", 1, "ORDER", "bad")), ErrorCode.VALIDATION_ERROR);
        }
    }

    private static InventoryCommand command(String request, String sku, long quantity, String type, String reference) {
        return new InventoryCommand(request, sku, quantity, type, reference);
    }

    private static void assertBusinessError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(expected));
    }
}
