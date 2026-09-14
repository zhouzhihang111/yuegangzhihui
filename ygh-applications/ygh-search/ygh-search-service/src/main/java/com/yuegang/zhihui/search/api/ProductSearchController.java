package com.yuegang.zhihui.search.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.search.application.ProductFullTextSearchService;
import com.yuegang.zhihui.search.security.SearchInternalSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class ProductSearchController {
    private final ProductFullTextSearchService service;
    private final SearchInternalSecurity security;

    public ProductSearchController(ProductFullTextSearchService service, SearchInternalSecurity security) {
        this.service = service;
        this.security = security;
    }

    @PostMapping("/internal/v1/search/products")
    ApiResponse<List<ProductSearchHit>> search(@Valid @RequestBody ProductSearchRequest command,
                                                HttpServletRequest request) {
        security.verify(request);
        return ApiResponse.success(service.search(command), TraceIdResolver.resolve(request));
    }
}
