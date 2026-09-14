package com.yuegang.zhihui.gateway;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Applies an API-only response policy after downstream processing. */
@Component
final class GatewaySecurityHeadersFilter implements WebFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            var headers = exchange.getResponse().getHeaders();
            headers.remove("Server");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Cross-Origin-Resource-Policy", "same-origin");
            headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            headers.set("Content-Security-Policy",
                    "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
            if (headers.getFirst(HttpHeaders.CACHE_CONTROL) == null) {
                headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
            }
            if ("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme())) {
                headers.set("Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains");
            } else {
                headers.remove("Strict-Transport-Security");
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }
}
