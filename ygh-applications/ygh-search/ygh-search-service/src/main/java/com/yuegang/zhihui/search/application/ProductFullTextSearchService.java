package com.yuegang.zhihui.search.application;

import com.yuegang.zhihui.search.api.ProductSearchHit;
import com.yuegang.zhihui.search.api.ProductSearchRequest;
import com.yuegang.zhihui.search.infrastructure.ElasticsearchRestClientFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.client.RestClient;

public final class ProductFullTextSearchService {
    private final RestClient elastic;
    private final String index;

    public ProductFullTextSearchService(String baseUrl, String index) {
        elastic = ElasticsearchRestClientFactory.create(baseUrl, "", "");
        this.index = index;
    }

    public List<ProductSearchHit> search(ProductSearchRequest request) {
        Map<?, ?> body = elastic.post().uri("/" + index + "/_search").body(Map.of(
                "size", request.limit(),
                "query", Map.of("multi_match", Map.of("query", request.keyword(),
                        "fields", List.of("title^4", "content"), "type", "best_fields")),
                "_source", List.of("documentId"))).retrieve().body(Map.class);
        if (body == null || !(body.get("hits") instanceof Map<?, ?> hits)
                || !(hits.get("hits") instanceof List<?> rows)) return List.of();
        var result = new ArrayList<ProductSearchHit>();
        for (Object value : rows) {
            if (!(value instanceof Map<?, ?> row) || !(row.get("_source") instanceof Map<?, ?> source)) continue;
            String documentId = Objects.toString(source.get("documentId"), "");
            if (!documentId.matches("product:[1-9][0-9]{0,18}")) continue;
            Object rawScore = row.get("_score");
            double score = rawScore instanceof Number number ? number.doubleValue() : 0;
            result.add(new ProductSearchHit(documentId.substring("product:".length()), score));
        }
        return List.copyOf(result);
    }
}
