package com.yuegang.zhihui.search.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.search.api.IndexVersionView;
import com.yuegang.zhihui.search.api.SwitchIndexRequest;
import com.yuegang.zhihui.search.infrastructure.ElasticsearchRestClientFactory;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

public final class IndexLifecycleService {
    private final JdbcTemplate jdbc;
    private final RestClient elastic;
    private final String alias;

    public IndexLifecycleService(JdbcTemplate jdbc, String baseUrl, String alias) {
        this.jdbc = jdbc;
        this.elastic = ElasticsearchRestClientFactory.create(baseUrl, "", "");
        this.alias = alias;
    }

    public IndexVersionView status() {
        return jdbc.query("SELECT alias_name,active_version,previous_version,switched_at," +
                        "(SELECT COUNT(*) FROM search_embedding e WHERE e.index_version=v.active_version) " +
                        "FROM search_index_version v WHERE alias_name=?", result -> {
            if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            return new IndexVersionView(result.getString(1), result.getString(2), result.getString(3),
                    result.getLong(5), result.getTimestamp(4).toLocalDateTime().atOffset(ZoneOffset.UTC));
        }, alias);
    }

    public void create(String version) {
        valid(version);
        var properties = new LinkedHashMap<String, Object>();
        properties.put("documentId", Map.of("type", "keyword"));
        properties.put("chunkId", Map.of("type", "keyword"));
        properties.put("title", Map.of("type", "text"));
        properties.put("content", Map.of("type", "text"));
        properties.put("category", Map.of("type", "keyword"));
        properties.put("visibility", Map.of("type", "keyword"));
        properties.put("documentVersion", Map.of("type", "long"));
        properties.put("sourceUpdatedAt", Map.of("type", "date"));
        elastic.put().uri("/" + version).body(Map.of("mappings", Map.of("properties", properties)))
                .retrieve().toBodilessEntity();
    }

    public IndexVersionView switchTo(SwitchIndexRequest command) {
        valid(command.version());
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM search_embedding WHERE index_version=?",
                Long.class, command.version());
        if (count == null || count < 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        var actions = List.of(
                Map.of("remove", Map.of("index", "*", "alias", alias, "must_exist", false)),
                Map.of("add", Map.of("index", command.version(), "alias", alias)));
        elastic.post().uri("/_aliases").body(Map.of("actions", actions)).retrieve().toBodilessEntity();
        if (jdbc.update("UPDATE search_index_version SET previous_version=active_version,active_version=?,switched_at=NOW() WHERE alias_name=?",
                command.version(), alias) != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        return status();
    }

    public void deletePrevious() {
        var current = status();
        if (current.previousVersion() == null) return;
        if (current.previousVersion().equals(current.activeVersion())) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        elastic.delete().uri("/" + current.previousVersion()).retrieve().toBodilessEntity();
        jdbc.update("DELETE FROM search_embedding WHERE index_version=?", current.previousVersion());
        jdbc.update("UPDATE search_index_version SET previous_version=NULL WHERE alias_name=?", alias);
    }

    private static void valid(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9._-]{1,63}"))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}
