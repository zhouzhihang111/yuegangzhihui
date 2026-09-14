package com.yuegang.zhihui.search.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.search.infrastructure.ElasticsearchRestClientFactory;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

public final class SearchDeletionService {
    private final JdbcTemplate jdbc;
    private final RestClient elastic;
    private final String alias;

    public SearchDeletionService(JdbcTemplate jdbc, String baseUrl, String alias) {
        this(jdbc, baseUrl, "", "", alias);
    }

    public SearchDeletionService(JdbcTemplate jdbc, String baseUrl, String username, String password, String alias) {
        this.jdbc = jdbc;
        this.elastic = ElasticsearchRestClientFactory.create(baseUrl, username, password);
        this.alias = alias;
    }

    public void deleteDocument(String document) {
        deleteDocument(document, null);
    }

    public void deleteDocument(String document, String indexName) {
        if (document == null || !document.matches("[A-Za-z0-9][A-Za-z0-9:._-]{0,63}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String target = indexName == null || indexName.isBlank() ? alias : indexName;
        if (!target.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        jdbc.update("DELETE FROM search_embedding WHERE document_id=?", document);
        if ("product-active".equals(target) && document.startsWith("product:")) {
            elastic.delete().uri("/" + target + "/_doc/" + document.substring("product:".length())
                    + "?refresh=true").retrieve().toBodilessEntity();
            return;
        }
        elastic.post().uri("/" + target + "/_delete_by_query?conflicts=proceed&refresh=true")
                .body(Map.of("query", Map.of("term", Map.of("documentId", document))))
                .retrieve().toBodilessEntity();
    }
}
