package com.yuegang.zhihui.search.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.search.api.IndexChunkCommand;
import com.yuegang.zhihui.search.api.SearchHit;
import com.yuegang.zhihui.search.api.SearchRequest;
import com.yuegang.zhihui.search.infrastructure.EmbeddingGateway;
import com.yuegang.zhihui.search.infrastructure.ElasticsearchRestClientFactory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

public final class HybridSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HybridSearchService.class);
    private final JdbcTemplate jdbc;
    private final RestClient elastic;
    private final EmbeddingGateway embeddings;
    private final String alias;

    public HybridSearchService(JdbcTemplate jdbc, String elasticBase, EmbeddingGateway embeddings, String alias) {
        this(jdbc, elasticBase, "", "", embeddings, alias);
    }

    public HybridSearchService(JdbcTemplate jdbc, String elasticBase, String username, String password,
                               EmbeddingGateway embeddings, String alias) {
        this.jdbc = jdbc;
        this.elastic = ElasticsearchRestClientFactory.create(elasticBase, username, password);
        this.embeddings = embeddings;
        this.alias = alias;
    }

    public List<SearchHit> search(SearchRequest request) {
        if (request.visibilities().isEmpty()) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        List<String> visibilities = request.visibilities().stream().sorted().toList();
        List<Raw> vectorHits = vectorSearch(request, visibilities);
        List<Raw> lexical = lexicalSearch(request, visibilities);
        if (vectorHits.isEmpty() && lexical.isEmpty()) {
            LOGGER.warn("hybrid_search_no_evidence alias={} vectorConfigured={}", alias, embeddings.configured());
        }
        return merge(request.limit(), lexical, vectorHits);
    }

    private List<Raw> vectorSearch(SearchRequest request, List<String> visibilities) {
        try {
            List<Double> vector = embeddings.embed(request.query());
            String literal = vector.toString();
            String markers = String.join(",", Collections.nCopies(visibilities.size(), "?"));
            List<Object> arguments = new ArrayList<>(List.of(literal, alias));
            arguments.addAll(visibilities);
            if (request.category() != null) arguments.add(request.category());
            arguments.add(literal);
            arguments.add(request.limit() * 2);
            return jdbc.query("""
                    SELECT document_id,chunk_id,title,content,document_version,source_updated_at,
                           1-(embedding <=> ?::vector) score
                    FROM search_embedding
                    WHERE index_version=(SELECT active_version FROM search_index_version WHERE alias_name=?)
                      AND embedding IS NOT NULL
                      AND visibility IN (%s)
                      %s
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """.formatted(markers, request.category() == null ? "" : "AND category=?"),
                    (row, index) -> new Raw(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                            row.getLong(5), offset(row.getObject(6)), 0, row.getDouble(7)), arguments.toArray());
        } catch (RuntimeException exception) {
            LOGGER.warn("vector_search_unavailable alias={} type={}", alias, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<Raw> lexicalSearch(SearchRequest request, List<String> visibilities) {
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("terms", Map.of("visibility", visibilities)));
        if (request.category() != null) {
            filters.add(Map.of("term", Map.of("category", request.category())));
        }
        try {
            Map<?, ?> body = elastic.post().uri("/" + alias + "/_search").body(Map.of(
                    "size", request.limit() * 2,
                    "query", Map.of("bool", Map.of(
                            "must", List.of(Map.of("multi_match", Map.of(
                                    "query", request.query(), "fields", List.of("title^3", "content")))),
                            "filter", filters))))
                    .retrieve().body(Map.class);
            return parse(body);
        } catch (RuntimeException exception) {
            LOGGER.warn("lexical_search_unavailable alias={} type={}", alias, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private static List<SearchHit> merge(int limit, List<Raw> lexical, List<Raw> vectorHits) {
        Map<String, Raw> merged = new LinkedHashMap<>();
        int rank = 1;
        for (Raw hit : lexical) {
            double rrf = 1d / (60 + rank++);
            merged.put(hit.key(), hit.withLexical(rrf));
        }
        rank = 1;
        for (Raw hit : vectorHits) {
            double rrf = 1d / (60 + rank++);
            merged.merge(hit.key(), hit.withVector(rrf), Raw::merge);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(Raw::finalScore).reversed())
                .limit(limit)
                .map(hit -> new SearchHit(hit.document, hit.chunk, hit.title, excerpt(hit.content),
                        hit.documentVersion, hit.updatedAt, hit.lexical, hit.vector, hit.finalScore()))
                .toList();
    }

    public void index(IndexChunkCommand command) {
        if (!command.published()) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        String storageVersion = "knowledge-active".equals(command.indexVersion())
                ? activeVersion("knowledge-active") : command.indexVersion();
        if (!"PRODUCT".equals(command.category())) {
            indexVector(command, storageVersion);
        }
        try {
            elastic.put().uri("/" + storageVersion + "/_doc/" + command.chunkId()).body(Map.of(
                    "documentId", command.documentId(), "chunkId", command.chunkId(), "title", command.title(),
                    "content", command.content(), "category", command.category(), "visibility", command.visibility(),
                    "documentVersion", command.documentVersion(), "sourceUpdatedAt",
                    command.sourceUpdatedAt() == null ? "" : command.sourceUpdatedAt().toString()))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            LOGGER.warn("lexical_index_unavailable indexVersion={} storageVersion={} documentId={} type={}",
                    command.indexVersion(), storageVersion, command.documentId(), exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }

    private void indexVector(IndexChunkCommand command, String storageVersion) {
        try {
            var vector = embeddings.embed(command.content());
            jdbc.update("INSERT INTO search_embedding(document_id,chunk_id,index_version,visibility,embedding,content_sha256,document_version,source_updated_at,title,category,content) VALUES(?,?,?,?,?::vector,encode(sha256(?::bytea),'hex'),?,?,?,?,?) ON CONFLICT(document_id,chunk_id,index_version) DO UPDATE SET embedding=EXCLUDED.embedding,visibility=EXCLUDED.visibility,document_version=EXCLUDED.document_version,source_updated_at=EXCLUDED.source_updated_at,title=EXCLUDED.title,category=EXCLUDED.category,content=EXCLUDED.content",
                    command.documentId(), command.chunkId(), storageVersion, command.visibility(),
                    vector.toString(), command.content().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    command.documentVersion(), command.sourceUpdatedAt(), command.title(), command.category(),
                    command.content());
        } catch (RuntimeException exception) {
            LOGGER.warn("vector_index_unavailable documentId={} type={}", command.documentId(),
                    exception.getClass().getSimpleName());
        }
    }

    private String activeVersion(String aliasName) {
        String version = jdbc.queryForObject(
                "SELECT active_version FROM search_index_version WHERE alias_name=?", String.class, aliasName);
        if (version == null || version.isBlank()) throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return version;
    }

    private static List<Raw> parse(Map<?, ?> body) {
        if (body == null) return List.of();
        var hits = (Map<?, ?>) body.get("hits");
        if (hits == null) return List.of();
        var rows = (List<?>) hits.get("hits");
        if (rows == null) return List.of();
        var out = new ArrayList<Raw>();
        for (Object value : rows) {
            var row = (Map<?, ?>) value;
            var source = (Map<?, ?>) row.get("_source");
            out.add(new Raw(Objects.toString(source.get("documentId")), Objects.toString(source.get("chunkId")),
                    Objects.toString(source.get("title")), Objects.toString(source.get("content")),
                    number(source.get("documentVersion")).longValue(), date(source.get("sourceUpdatedAt")),
                    ((Number) row.get("_score")).doubleValue(), 0));
        }
        return out;
    }

    private static Number number(Object value) { return value instanceof Number number ? number : 0; }
    private static OffsetDateTime date(Object value) {
        try {
            String text = Objects.toString(value, "");
            return text.isBlank() ? null : OffsetDateTime.parse(text);
        } catch (Exception ignored) { return null; }
    }
    private static OffsetDateTime offset(Object value) {
        if (value instanceof OffsetDateTime date) return date;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        return date(value);
    }
    private static String excerpt(String value) { return value.length() <= 300 ? value : value.substring(0, 300); }

    private record Raw(String document, String chunk, String title, String content, long documentVersion,
                       OffsetDateTime updatedAt, double lexical, double vector) {
        String key() { return document + ":" + chunk; }
        Raw withLexical(double score) { return new Raw(document, chunk, title, content, documentVersion, updatedAt, score, vector); }
        Raw withVector(double score) { return new Raw(document, chunk, title, content, documentVersion, updatedAt, lexical, score); }
        Raw merge(Raw other) { return new Raw(document, chunk, title.isBlank() ? other.title : title,
                content.isBlank() ? other.content : content, documentVersion == 0 ? other.documentVersion : documentVersion,
                updatedAt == null ? other.updatedAt : updatedAt, lexical + other.lexical, vector + other.vector); }
        double finalScore() { return lexical + vector; }
    }
}
