package com.yuegang.zhihui.knowledge.application;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import com.yuegang.zhihui.search.api.SearchHit;
import com.yuegang.zhihui.search.api.SearchRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.client.RestClient;

public final class KnowledgeSearchGateway {
    private static final String PATH = "/internal/v1/search/hybrid";
    private final RestClient client;
    private final InternalServiceSignature signatures;

    public KnowledgeSearchGateway(String baseUrl, byte[] secret) {
        client = RestClient.builder().baseUrl(baseUrl).build();
        signatures = new InternalServiceSignature(secret, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    public List<SearchHit> search(String query, String category, int limit, Set<String> visibilities) {
        Instant now = Instant.now();
        var metadata = new InternalServiceSignature.Metadata("ygh-knowledge-service", "POST", PATH, now);
        @SuppressWarnings("unchecked")
        ApiResponse<List<Map<String, Object>>> response = client.post().uri(PATH)
                .header("X-YGH-Service", "ygh-knowledge-service")
                .header("X-YGH-Service-Timestamp", Long.toString(now.toEpochMilli()))
                .header("X-YGH-Service-Signature", signatures.sign(metadata))
                .body(new SearchRequest(query, limit, category, visibilities))
                .retrieve().body(ApiResponse.class);
        if (response == null || response.data() == null) return List.of();
        return response.data().stream().map(KnowledgeSearchGateway::hit).toList();
    }

    private static SearchHit hit(Map<String, Object> value) {
        return new SearchHit(Objects.toString(value.get("documentId")), Objects.toString(value.get("chunkId")),
                Objects.toString(value.get("title")), Objects.toString(value.get("excerpt")),
                number(value.get("documentVersion")).longValue(), date(value.get("sourceUpdatedAt")),
                number(value.get("lexicalScore")).doubleValue(), number(value.get("vectorScore")).doubleValue(),
                number(value.get("finalScore")).doubleValue());
    }

    private static Number number(Object value) { return value instanceof Number number ? number : 0; }

    private static OffsetDateTime date(Object value) {
        try {
            String text = Objects.toString(value, "");
            return text.isBlank() ? null : OffsetDateTime.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
