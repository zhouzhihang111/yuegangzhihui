package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.redis.ReactiveSessionValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(prefix = "ygh.security.session-validation", name = "enabled", havingValue = "true")
final class JwtSessionValidationFilter implements GlobalFilter, Ordered {
    private final ReactiveSessionValidator sessions;
    private final GatewaySecurityErrorWriter errors;

    JwtSessionValidationFilter(ReactiveSessionValidator sessions, GatewaySecurityErrorWriter errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication -> authentication != null && authentication.isAuthenticated())
                .map(authentication -> authentication.getPrincipal())
                .filter(Jwt.class::isInstance)
                .cast(Jwt.class)
                .flatMap(jwt -> validate(jwt, exchange, chain).thenReturn(true))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(false)))
                .then();
    }

    private Mono<Void> validate(Jwt jwt, ServerWebExchange exchange, GatewayFilterChain chain) {
        long accountId;
        try {
            accountId = Long.parseLong(jwt.getClaimAsString("account_id"));
        } catch (RuntimeException invalidClaim) {
            return errors.unauthenticated(exchange);
        }
        return sessions.valid(accountId, jwt.getId())
                .map(valid -> valid ? SessionCheck.ACTIVE : SessionCheck.REJECTED)
                .onErrorReturn(SessionCheck.UNAVAILABLE)
                .flatMap(check -> switch (check) {
                    case ACTIVE -> chain.filter(exchange);
                    case REJECTED -> errors.unauthenticated(exchange);
                    case UNAVAILABLE -> errors.dependencyUnavailable(exchange);
                });
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 15; }

    private enum SessionCheck { ACTIVE, REJECTED, UNAVAILABLE }
}
