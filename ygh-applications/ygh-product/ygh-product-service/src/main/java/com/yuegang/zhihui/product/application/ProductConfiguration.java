package com.yuegang.zhihui.product.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.product.security.ProductAdminVerifier;
import java.util.Arrays;
import java.util.Base64;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
class ProductConfiguration {
    @Bean
    ProductSearchGateway productSearchGateway(@Value("${ygh.product.search-base-url}") String base,
                                              @Value("${YGH_INTERNAL_REQUEST_HMAC_BASE64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        try { return new ProductSearchGateway(base, key); }
        finally { Arrays.fill(key, (byte) 0); }
    }

    @Bean ProductService productService(DataSource dataSource, ProductSearchGateway search) {
        return new ProductService(dataSource, search);
    }

    @Bean ProductAdministrationService productAdministrationService(DataSource dataSource, ProductService products,
                                                                     ObjectMapper json) {
        return new ProductAdministrationService(dataSource, products, json);
    }

    @Bean ProductCacheService productCacheService(StringRedisTemplate redis, ObjectMapper json) {
        return new ProductCacheService(redis, json);
    }

    @Bean CatalogService catalogService(DataSource dataSource, ObjectMapper json) {
        return new CatalogService(dataSource, json);
    }

    @Bean ProductSearchDispatcher productSearchDispatcher(JdbcTemplate jdbc,
                                                            @Value("${ygh.product.search-base-url}") String base,
                                                            @Value("${YGH_INTERNAL_REQUEST_HMAC_BASE64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        try { return new ProductSearchDispatcher(jdbc, base, key); }
        finally { Arrays.fill(key, (byte) 0); }
    }

    @Bean ProductAdminVerifier productAdminVerifier(@Value("${YGH_INTERNAL_REQUEST_HMAC_BASE64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        try { return new ProductAdminVerifier(key); }
        finally { Arrays.fill(key, (byte) 0); }
    }
}
