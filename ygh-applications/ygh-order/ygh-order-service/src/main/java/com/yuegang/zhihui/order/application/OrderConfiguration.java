package com.yuegang.zhihui.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.order.infrastructure.CommerceReconciliationClient;
import com.yuegang.zhihui.order.infrastructure.InventoryClient;
import com.yuegang.zhihui.order.security.OrderUserResolver;
import java.util.Arrays;
import java.util.Base64;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OrderConfiguration {
    @Bean OrderService orderService(DataSource dataSource) { return new OrderService(dataSource); }
    @Bean OrderQueryService orderQueryService(DataSource dataSource) { return new OrderQueryService(dataSource); }
    @Bean OrderInventoryFacade orderInventoryFacade(OrderService orders, InventoryClient inventory,
                                                     DataSource dataSource) {
        return new OrderInventoryFacade(orders, inventory, dataSource);
    }
    @Bean RefundInventoryCoordinator refundInventoryCoordinator(OrderService orders, InventoryClient inventory,
                                                                  DataSource dataSource) {
        return new RefundInventoryCoordinator(orders, inventory, dataSource);
    }
    @Bean InventoryClient inventoryClient(@Value("${ygh.inventory.internal-base-url}") String base,
                                           @Value("${ygh.internal-request.hmac-base64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        try { return new InventoryClient(base, key); }
        finally { Arrays.fill(key, (byte) 0); }
    }
    @Bean CommerceReconciliationClient commerceReconciliationClient(
            @Value("${ygh.wallet.internal-base-url}") String wallet,
            @Value("${ygh.inventory.internal-base-url}") String inventory,
            @Value("${ygh.internal-request.hmac-base64}") String encoded, ObjectMapper json) {
        byte[] key = Base64.getDecoder().decode(encoded);
        try { return new CommerceReconciliationClient(wallet, inventory, key, json); }
        finally { Arrays.fill(key, (byte) 0); }
    }
    @Bean CommerceReconciliationService commerceReconciliationService(DataSource dataSource,
                                                                        CommerceReconciliationClient client) {
        return new CommerceReconciliationService(dataSource, client);
    }
    @Bean OrderPaymentConfirmationService orderPaymentConfirmationService(
            OrderService orders, OrderInventoryFacade inventory, CommerceReconciliationClient client) {
        return new OrderPaymentConfirmationService(orders, inventory, client);
    }
    @Bean CartService cartService(DataSource dataSource) { return new CartService(dataSource); }
    @Bean CheckoutService checkoutService(@Value("${ygh.product.internal-base-url}") String products,
                                           InventoryClient inventory) {
        return new CheckoutService(products, inventory);
    }
    @Bean OrderUserResolver orderUserResolver(@Value("${ygh.internal-request.hmac-base64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        try { return new OrderUserResolver(key); }
        finally { Arrays.fill(key, (byte) 0); }
    }
}
