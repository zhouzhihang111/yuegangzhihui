package com.yuegang.zhihui.order.application;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.order.api.CreateOrderRequest;
import com.yuegang.zhihui.order.api.OrderItemCommand;
import com.yuegang.zhihui.order.api.OrderPreviewView;
import com.yuegang.zhihui.order.infrastructure.InventoryClient;
import com.yuegang.zhihui.product.api.ProductStatus;
import com.yuegang.zhihui.product.api.ProductView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public final class CheckoutService {
    private final RestClient products;
    private final InventoryClient inventory;

    public CheckoutService(String baseUrl) { this(baseUrl, null); }

    public CheckoutService(String baseUrl, InventoryClient inventory) {
        products = RestClient.builder().baseUrl(baseUrl).build();
        this.inventory = inventory;
    }

    public OrderPreviewView preview(CreateOrderRequest command) {
        var items = new ArrayList<OrderItemCommand>();
        BigDecimal total = BigDecimal.ZERO;
        for (var requested : command.items()) {
            ApiResponse<ProductView> response;
            try {
                response = products.get().uri("/api/v1/products/" + requested.skuId())
                        .retrieve().body(new ParameterizedTypeReference<>() { });
            } catch (RestClientResponseException e) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            }
            if (response == null || response.data() == null
                    || response.data().status() != ProductStatus.PUBLISHED) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            }
            if (inventory != null) {
                try {
                    if (inventory.get(requested.skuId()).available() < requested.quantity()) {
                        throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
                    }
                } catch (RestClientResponseException e) {
                    throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
                }
            }
            ProductView product = response.data();
            var item = new OrderItemCommand(product.skuId(), product.skuCode(), product.name(), product.price(),
                    requested.quantity());
            items.add(item);
            total = total.add(product.price().multiply(BigDecimal.valueOf(requested.quantity())));
        }
        return new OrderPreviewView(List.copyOf(items), total, "CNY", command.address(), List.of());
    }

    public CreateOrderRequest trusted(CreateOrderRequest command) {
        OrderPreviewView preview = preview(command);
        return new CreateOrderRequest(command.requestId(), preview.items(), preview.address(), command.remark());
    }
}
