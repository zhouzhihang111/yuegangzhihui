package com.yuegang.zhihui.product.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.product.application.*;
import com.yuegang.zhihui.product.security.ProductAdminVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public final class ProductController {
    private final ProductService service; private final ProductCacheService cache; private final ProductAdminVerifier admin;
    public ProductController(ProductService service, ProductCacheService cache, ProductAdminVerifier admin) { this.service = service; this.cache = cache; this.admin = admin; }

    @GetMapping("/api/v1/products")
    ApiResponse<List<ProductView>> list(@RequestParam(required = false) String category, @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                                        @RequestParam(required = false) String origin, @RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        List<ProductView> result = minPrice == null && maxPrice == null && (origin == null || origin.isBlank())
                ? cache.list(category, keyword, limit, () -> service.list(category, keyword, limit, true))
                : service.list(category, keyword, minPrice, maxPrice, origin, null, limit, true);
        return ok(result, request);
    }

    @GetMapping("/api/v1/products/{sku}") ApiResponse<ProductView> get(@PathVariable String sku, HttpServletRequest request) { return ok(cache.detail(sku, () -> service.get(sku, true)), request); }
    @PostMapping("/api/v1/admin/products") ApiResponse<ProductView> create(@Valid @RequestBody SaveProductRequest command, HttpServletRequest request) { admin.verify(request); ProductView result = service.create(command); cache.invalidate(result.skuId()); return ok(result, request); }

    @GetMapping("/api/v1/admin/products")
    ApiResponse<List<ProductView>> adminList(@RequestParam(required = false) String category, @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
                                             @RequestParam(required = false) String origin, @RequestParam(required = false) ProductStatus status,
                                             @RequestParam(defaultValue = "50") int limit, HttpServletRequest request) {
        admin.verify(request); return ok(service.list(category, keyword, minPrice, maxPrice, origin, status, limit, false), request);
    }

    @PutMapping("/api/v1/admin/products/{sku}/status") ApiResponse<ProductView> status(@PathVariable String sku, @RequestParam ProductStatus status, @RequestParam long version, HttpServletRequest request) { admin.verify(request); ProductView result = service.changeStatus(sku, status, version); cache.invalidate(sku); return ok(result, request); }
    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) { return ApiResponse.success(data, TraceIdResolver.resolve(request)); }
}
