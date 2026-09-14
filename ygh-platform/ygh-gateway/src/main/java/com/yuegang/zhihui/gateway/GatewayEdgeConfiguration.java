package com.yuegang.zhihui.gateway;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** Binds bounded request policies without exposing mutable configuration state. */
@Configuration(proxyBeanMethods = false)
class GatewayEdgeConfiguration {

    @Bean
    GatewayRequestGuardFilter gatewayRequestGuardFilter(
            @Value("${ygh.gateway.request.max-size:2MB}") DataSize requestMaxSize,
            @Value("${ygh.gateway.request.upload-max-size:50MB}") DataSize uploadMaxSize,
            @Value("${ygh.gateway.request.upload-paths}") String uploadPaths,
            GatewaySecurityErrorWriter errorWriter) {
        return new GatewayRequestGuardFilter(
                requestMaxSize.toBytes(), uploadMaxSize.toBytes(), parsePaths(uploadPaths), errorWriter);
    }

    static Set<String> parsePaths(String configuredPaths) {
        var paths = new LinkedHashSet<String>();
        Arrays.stream(configuredPaths.split(","))
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .forEach(paths::add);
        return Set.copyOf(paths);
    }
}
