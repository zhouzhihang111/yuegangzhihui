package com.yuegang.zhihui.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.test.YghTestContainerFactory;
import com.yuegang.zhihui.inventory.api.InventoryCommand;
import com.yuegang.zhihui.inventory.api.InventoryReferenceView;
import com.yuegang.zhihui.order.api.AddressSnapshot;
import com.yuegang.zhihui.order.api.CartItemRequest;
import com.yuegang.zhihui.order.api.CreateOrderRequest;
import com.yuegang.zhihui.order.api.OrderItemCommand;
import com.yuegang.zhihui.order.api.OrderStatus;
import com.yuegang.zhihui.order.infrastructure.CommerceReconciliationClient;
import com.yuegang.zhihui.order.infrastructure.InventoryClient;
import com.yuegang.zhihui.wallet.api.WalletReferenceView;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OrderServicesIntegrationTest {
    @Test
    void supportsCartSnapshotsStateMachineInventoryCompensationExpiryAndReconciliation() throws Exception {
        try (var mysql = YghTestContainerFactory.mysql().start()) {
            Flyway.configure().dataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential())
                    .locations("classpath:db/migration").load().migrate();
            var dataSource = new DriverManagerDataSource(mysql.jdbcUrl(), mysql.username(), mysql.credential());
            var orders = new OrderService(dataSource);
            var query = new OrderQueryService(dataSource);
            var cart = new CartService(dataSource);
            var inventory = mock(InventoryClient.class);
            var facade = new OrderInventoryFacade(orders, inventory, dataSource);
            var refunds = new RefundInventoryCoordinator(orders, inventory, dataSource);
            var fulfillment = new OrderFulfillmentService(dataSource);

            var cartItem = cart.save(42, new CartItemRequest("1001", 2, true, 0));
            assertThat(cart.list(42)).containsExactly(cartItem);
            var changedCart = cart.save(42, new CartItemRequest("1001", 3, false, cartItem.version()));
            assertThat(changedCart.quantity()).isEqualTo(3);
            assertBusinessError(() -> cart.save(42, new CartItemRequest("1001", 1, true, 99)), ErrorCode.BUSINESS_CONFLICT);
            cart.delete(42, changedCart.id(), changedCart.version());
            assertThat(cart.list(42)).isEmpty();
            assertBusinessError(() -> cart.delete(42, changedCart.id(), changedCart.version()), ErrorCode.RESOURCE_NOT_FOUND);

            var firstRequest = request("create-1", "1001", 2, "19.90");
            cart.save(42, new CartItemRequest("1001", 2, true, 0));
            var unrelatedCartItem = cart.save(42, new CartItemRequest("2000", 1, true, 0));
            var first = facade.create(42, firstRequest);
            assertThat(first.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(first.totalAmount()).isEqualByComparingTo("39.80");
            assertThat(cart.list(42)).containsExactly(unrelatedCartItem);
            assertThat(facade.create(42, firstRequest).orderId()).isEqualTo(first.orderId());
            assertThat(cart.list(42)).containsExactly(unrelatedCartItem);
            verify(inventory, times(2)).reserve(any(InventoryCommand.class));
            assertThat(query.mine(42, null, 0)).containsExactly(first);
            assertThat(query.admin("PENDING_PAYMENT", 1000)).containsExactly(first);
            assertBusinessError(() -> orders.get(84, first.orderId()), ErrorCode.RESOURCE_NOT_FOUND);

            facade.paymentSucceeded("payment-event-1", first.orderId());
            facade.paymentSucceeded("payment-event-1", first.orderId());
            verify(inventory, times(2)).confirm(any(InventoryCommand.class));
            var paid = orders.get(42, first.orderId());
            assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
            var processing = fulfillment.start(first.orderId(), paid.version());
            assertThat(processing.status()).isEqualTo(OrderStatus.PROCESSING);
            assertThat(fulfillment.complete(first.orderId(), processing.version()).status()).isEqualTo(OrderStatus.COMPLETED);
            assertBusinessError(() -> fulfillment.start(first.orderId(), paid.version()), ErrorCode.BUSINESS_CONFLICT);
            assertBusinessError(() -> fulfillment.start("invalid", 0), ErrorCode.VALIDATION_ERROR);

            var cancelledOrder = facade.create(42, request("create-cancel", "1002", 1, "9.00"));
            assertThat(facade.cancel(42, cancelledOrder.orderId(), cancelledOrder.version()).status()).isEqualTo(OrderStatus.CANCELLED);
            verify(inventory).release(any(InventoryCommand.class));
            assertBusinessError(() -> orders.cancel(42, cancelledOrder.orderId(), cancelledOrder.version()), ErrorCode.BUSINESS_CONFLICT);

            var refundedOrder = facade.create(42, request("create-refund", "1003", 1, "25.00"));
            facade.paymentSucceeded("payment-event-refund", refundedOrder.orderId());
            var paidForRefund = orders.get(42, refundedOrder.orderId());
            var refunding = orders.requestRefund(42, refundedOrder.orderId(), paidForRefund.version());
            assertThat(refunding.status()).isEqualTo(OrderStatus.REFUNDING);
            refunds.refundSucceeded("refund-event-1", refundedOrder.orderId());
            refunds.refundSucceeded("refund-event-1", refundedOrder.orderId());
            assertThat(orders.get(42, refundedOrder.orderId()).status()).isEqualTo(OrderStatus.REFUNDED);
            verify(inventory, times(2)).returnSold(any(InventoryCommand.class));

            var expired = facade.create(42, request("create-expired", "1004", 1, "5.00"));
            try (var connection = DriverManager.getConnection(mysql.jdbcUrl(), mysql.username(), mysql.credential())) {
                connection.createStatement().executeUpdate("UPDATE order_main SET expires_at=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=" + expired.orderId());
            }
            assertThat(facade.closeExpired()).isEqualTo(1);
            assertThat(orders.get(42, expired.orderId()).status()).isEqualTo(OrderStatus.CLOSED);

            var reconciliationClient = mock(CommerceReconciliationClient.class);
            when(reconciliationClient.wallet(any())).thenReturn(new WalletReferenceView("ref", "42", false, false,
                    BigDecimal.ZERO, BigDecimal.ZERO, "CNY"));
            when(reconciliationClient.inventory(any())).thenReturn(new InventoryReferenceView("ref", 0, 0, 0, 0));
            var reconciliation = new CommerceReconciliationService(dataSource, reconciliationClient).run();
            assertThat(reconciliation.checkedOrders()).isEqualTo(4);
            assertThat(reconciliation.discrepancies()).isNotEmpty();

            doThrow(new IllegalStateException("reserve failed")).when(inventory).reserve(any());
            assertThatThrownBy(() -> facade.create(42, request("create-failed", "1005", 1, "1.00")))
                    .isInstanceOf(IllegalStateException.class);
            assertBusinessError(() -> orders.get(42, "0"), ErrorCode.VALIDATION_ERROR);
        }
    }

    private static CreateOrderRequest request(String requestId, String sku, long quantity, String price) {
        return new CreateOrderRequest(requestId,
                List.of(new OrderItemCommand(sku, "SKU-" + sku, "商品" + sku, new BigDecimal(price), quantity)),
                new AddressSnapshot("张三", "13800138000", "CN", "440000", "广东省", "广州市", "天河区", "测试地址", "510000"),
                "测试订单");
    }

    private static void assertBusinessError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.errorCode()).isEqualTo(expected));
    }
}
