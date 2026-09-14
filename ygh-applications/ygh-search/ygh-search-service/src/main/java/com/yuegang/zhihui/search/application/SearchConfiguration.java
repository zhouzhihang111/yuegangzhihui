package com.yuegang.zhihui.search.application;

import com.yuegang.zhihui.search.infrastructure.DynamicDoubaoEmbeddingGateway;
import com.yuegang.zhihui.search.infrastructure.EmbeddingGateway;
import com.yuegang.zhihui.search.infrastructure.SystemAiProviderConfigClient;
import com.yuegang.zhihui.search.security.SearchInternalSecurity;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
class SearchConfiguration {
    @Bean
    SystemAiProviderConfigClient systemAiProviderConfigClient(
            @Value("${ygh.search.system-base-url}") String baseUrl,
            @Value("${ygh.internal-request.hmac-base64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        try { return new SystemAiProviderConfigClient(baseUrl, key); }
        finally { Arrays.fill(key, (byte) 0); }
    }

    @Bean EmbeddingGateway embeddingGateway(SystemAiProviderConfigClient configs) {
        return new DynamicDoubaoEmbeddingGateway(configs);
    }
    @Bean HybridSearchService hybridSearchService(JdbcTemplate jdbc,
            @Value("${ygh.search.elasticsearch-base-url}") String elastic,
            EmbeddingGateway embeddings,
            @Value("${ygh.search.index-alias:knowledge-active}") String alias) {
        return new HybridSearchService(jdbc, elastic, embeddings, alias);
    }
    @Bean ProductFullTextSearchService productFullTextSearchService(
            @Value("${ygh.search.elasticsearch-base-url}") String elastic,
            @Value("${ygh.search.product-index:product-active}") String index) {
        return new ProductFullTextSearchService(elastic, index);
    }
    @Bean SearchDeletionService searchDeletionService(JdbcTemplate jdbc,
            @Value("${ygh.search.elasticsearch-base-url}") String elastic,
            @Value("${ygh.search.index-alias:knowledge-active}") String alias) {
        return new SearchDeletionService(jdbc, elastic, alias);
    }
    @Bean IndexLifecycleService indexLifecycleService(JdbcTemplate jdbc,
            @Value("${ygh.search.elasticsearch-base-url}") String elastic,
            @Value("${ygh.search.index-alias:knowledge-active}") String alias) {
        return new IndexLifecycleService(jdbc, elastic, alias);
    }
    @Bean SearchInternalSecurity searchInternalSecurity(
            @Value("${ygh.internal-request.hmac-base64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        try { return new SearchInternalSecurity(key); }
        finally { Arrays.fill(key, (byte) 0); }
    }
}
