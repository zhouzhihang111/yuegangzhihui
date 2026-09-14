package com.yuegang.zhihui.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Bridges an authenticated resource-server JWT into the trusted Gateway attribute. */
@Component
final class JwtPrincipalBridgeFilter implements GlobalFilter, Ordered {

    private final JwtPrincipalMapper principalMapper;

    JwtPrincipalBridgeFilter(JwtPrincipalMapper principalMapper) {
        this.principalMapper = principalMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication != null && authentication.isAuthenticated())
                .map(authentication -> authentication.getPrincipal())
                .filter(Jwt.class::isInstance)
                .cast(Jwt.class)
                .doOnNext(jwt ->
                    exchange.getAttributes().put(
                            GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL,
                            principalMapper.map(jwt)))
                .then(Mono.defer(() -> chain.filter(exchange)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
