package com.yuegang.zhihui.product.application;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.search.api.ProductSearchRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.client.RestClient;

public final class ProductSearchGateway {
    private static final String PATH = "/internal/v1/search/products";
    private final RestClient client;
    private final InternalServiceSignature signatures;

    public ProductSearchGateway(String baseUrl, byte[] secret) {
        client = RestClient.builder().baseUrl(baseUrl).build();
        signatures = new InternalServiceSignature(secret, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    public List<String> search(String keyword, int limit) {
        Instant now = Instant.now();
        var metadata = new InternalServiceSignature.Metadata("ygh-product-service", "POST", PATH, now);
        @SuppressWarnings("unchecked")
        ApiResponse<List<Map<String, Object>>> response = client.post().uri(PATH)
                .header("X-YGH-Service", "ygh-product-service")
                .header("X-YGH-Service-Timestamp", Long.toString(now.toEpochMilli()))
                .header("X-YGH-Service-Signature", signatures.sign(metadata))
                .body(new ProductSearchRequest(keyword, limit)).retrieve().body(ApiResponse.class);
        if (response == null || response.data() == null) return List.of();
        return response.data().stream().map(value -> Objects.toString(value.get("skuId"), ""))
                .filter(value -> value.matches("[1-9][0-9]{0,18}")).toList();
    }
}
