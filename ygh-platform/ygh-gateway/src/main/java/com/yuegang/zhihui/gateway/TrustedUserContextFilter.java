package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Propagates identity only from a server-authenticated exchange attribute. */
@Component
final class TrustedUserContextFilter implements GlobalFilter, Ordered {

    private static final int MAX_AUTHORITIES = 128;
    private static final int MAX_AUTHORITY_HEADER_LENGTH = 4096;
    private static final Pattern SAFE_USER_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SAFE_AUTHORITY = Pattern.compile("[A-Za-z][A-Za-z0-9:_-]{0,127}");
    private final InternalUserContextSignature signatures;
    private final Clock clock;

    TrustedUserContextFilter() { this(Base64.getEncoder().encodeToString(new byte[32]), Clock.systemUTC()); }
    @Autowired
    TrustedUserContextFilter(@Value("${ygh.internal-request.hmac-base64}") String encodedSecret) {
        this(encodedSecret, Clock.systemUTC());
    }
    TrustedUserContextFilter(String encodedSecret, Clock clock) {
        byte[] secret;
        try { secret = Base64.getDecoder().decode(encodedSecret); }
        catch (IllegalArgumentException malformed) { throw new IllegalStateException("internal HMAC secret is malformed", malformed); }
        try { this.signatures = new InternalUserContextSignature(secret, clock, Duration.ofSeconds(30)); }
        finally { Arrays.fill(secret, (byte) 0); }
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        CurrentUserPrincipal principal = exchange.getAttribute(
                GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL);
        String userId = principal == null ? null : validatedUserId(principal.userId());
        String roles = principal == null ? "" : encodedAuthorities(principal.roles(), "roles");
        String permissions = principal == null ? "" : encodedAuthorities(principal.permissions(), "permissions");
        Instant timestamp = clock.instant();
        String signature = principal == null ? null : signatures.sign(new InternalUserContextSignature.Metadata(
                userId, split(roles), split(permissions),
                exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID),
                exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID),
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().pathWithinApplication().value(), timestamp));
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(GatewayHeaders.USER_ID);
            headers.remove(GatewayHeaders.ROLES);
            headers.remove(GatewayHeaders.PERMISSIONS);
            headers.remove(GatewayHeaders.USER_CONTEXT_TIMESTAMP);
            headers.remove(GatewayHeaders.USER_CONTEXT_SIGNATURE);
            if (principal != null) {
                headers.set(GatewayHeaders.USER_ID, userId);
                if (!roles.isEmpty()) {
                    headers.set(GatewayHeaders.ROLES, roles);
                }
                if (!permissions.isEmpty()) {
                    headers.set(GatewayHeaders.PERMISSIONS, permissions);
                }
                headers.set(GatewayHeaders.USER_CONTEXT_TIMESTAMP, Long.toString(timestamp.toEpochMilli()));
                headers.set(GatewayHeaders.USER_CONTEXT_SIGNATURE, signature);
            }
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static List<String> split(String encoded) {
        return encoded.isEmpty() ? List.of() : List.of(encoded.split(",", -1));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    private static String validatedUserId(String userId) {
        if (!SAFE_USER_ID.matcher(userId).matches()) {
            throw new IllegalArgumentException("trusted userId contains unsafe characters");
        }
        return userId;
    }

    private static String encodedAuthorities(Set<String> authorities, String fieldName) {
        if (authorities.size() > MAX_AUTHORITIES) {
            throw new IllegalArgumentException(fieldName + " exceeds authority count limit");
        }
        String encoded = authorities.stream()
                .peek(value -> {
                    if (!SAFE_AUTHORITY.matcher(value).matches()) {
                        throw new IllegalArgumentException(fieldName + " contains an unsafe authority");
                    }
                })
                .sorted()
                .collect(Collectors.joining(","));
        if (encoded.length() > MAX_AUTHORITY_HEADER_LENGTH) {
            throw new IllegalArgumentException(fieldName + " exceeds header length limit");
        }
        return encoded;
    }
}
