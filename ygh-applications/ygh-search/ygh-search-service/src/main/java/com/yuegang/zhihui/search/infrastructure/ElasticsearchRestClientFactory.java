package com.yuegang.zhihui.search.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

public final class ElasticsearchRestClientFactory {
    private ElasticsearchRestClientFactory() {}

    public static RestClient create(String baseUrl, String username, String password) {
        if (username == null || username.isBlank()) {
            username = setting("YGH_ELASTICSEARCH_USERNAME", "YGH_SEARCH_ES_USERNAME");
            password = setting("YGH_ELASTICSEARCH_PASSWORD", "YGH_SEARCH_ES_PASSWORD");
        }
        var builder = RestClient.builder().baseUrl(baseUrl);
        if (username != null && !username.isBlank()) {
            String credentials = username + ":" + (password == null ? "" : password);
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder.build();
    }

    private static String setting(String primary, String fallback) {
        String value = System.getProperty(primary);
        if (value == null || value.isBlank()) {
            value = System.getenv(primary);
        }
        if (value == null || value.isBlank()) {
            value = System.getProperty(fallback);
        }
        if (value == null || value.isBlank()) {
            value = System.getenv(fallback);
        }
        return value == null ? "" : value;
    }
}
