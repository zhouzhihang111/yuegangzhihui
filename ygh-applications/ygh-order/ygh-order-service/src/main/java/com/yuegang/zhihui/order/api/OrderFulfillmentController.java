package com.yuegang.zhihui.order.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.order.application.OrderFulfillmentService;
import com.yuegang.zhihui.order.security.OrderUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
public final class OrderFulfillmentController {
    private final OrderFulfillmentService service;
    private final OrderUserResolver users;

    public OrderFulfillmentController(OrderFulfillmentService service, OrderUserResolver users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/{id}/simulate-processing")
    ApiResponse<OrderView> start(@PathVariable String id, @RequestParam long version, HttpServletRequest request) {
        users.requirePermission(request, "order:order:fulfill");
        return ApiResponse.success(service.start(id, version), TraceIdResolver.resolve(request));
    }

    @PostMapping("/{id}/simulate-completion")
    ApiResponse<OrderView> complete(@PathVariable String id, @RequestParam long version, HttpServletRequest request) {
        users.requirePermission(request, "order:order:fulfill");
        return ApiResponse.success(service.complete(id, version), TraceIdResolver.resolve(request));
    }
}
