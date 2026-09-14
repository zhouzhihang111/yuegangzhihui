package com.yuegang.zhihui.gateway;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Establishes bounded correlation identifiers and removes all client-supplied
 * internal identity headers before authentication executes.
 */
@Component
final class CorrelationIdFilter implements WebFilter, Ordered {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,63}");
    private final Supplier<String> idGenerator;

    CorrelationIdFilter() {
        this(() -> UUID.randomUUID().toString().replace("-", ""));
    }

    CorrelationIdFilter(Supplier<String> idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = resolveId(exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID));
        String requestId = resolveId(exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID));

        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.set(GatewayHeaders.TRACE_ID, traceId);
            headers.set(GatewayHeaders.REQUEST_ID, requestId);
            headers.remove(GatewayHeaders.USER_ID);
            headers.remove(GatewayHeaders.ROLES);
            headers.remove(GatewayHeaders.PERMISSIONS);
            headers.remove(GatewayHeaders.USER_CONTEXT_TIMESTAMP);
            headers.remove(GatewayHeaders.USER_CONTEXT_SIGNATURE);
        }).build();
        var correlated = exchange.mutate().request(request).build();
        correlated.getAttributes().put(GatewaySecurityAttributes.TRACE_ID, traceId);
        correlated.getAttributes().put(GatewaySecurityAttributes.REQUEST_ID, requestId);
        correlated.getResponse().getHeaders().set(GatewayHeaders.TRACE_ID, traceId);
        correlated.getResponse().getHeaders().set(GatewayHeaders.REQUEST_ID, requestId);
        correlated.getResponse().beforeCommit(() -> {
            correlated.getResponse().getHeaders().set(GatewayHeaders.TRACE_ID, traceId);
            correlated.getResponse().getHeaders().set(GatewayHeaders.REQUEST_ID, requestId);
            return Mono.empty();
        });

        return chain.filter(correlated).contextWrite(context -> context
                .put(GatewaySecurityAttributes.TRACE_ID, traceId)
                .put(GatewaySecurityAttributes.REQUEST_ID, requestId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private String resolveId(String candidate) {
        if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        String generated = idGenerator.get();
        if (generated == null || !SAFE_ID.matcher(generated).matches()) {
            throw new IllegalStateException("Correlation ID generator returned an unsafe value");
        }
        return generated;
    }
}
