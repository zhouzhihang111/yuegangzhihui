package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.security.InternalRequestSignature;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Replaces client-supplied IP metadata with a signed edge-observed address. */
@Component
@ConditionalOnProperty(prefix = "ygh.internal-request", name = "enabled", havingValue = "true", matchIfMissing = true)
final class TrustedClientIpFilter implements GlobalFilter, Ordered {
    private final InternalRequestSignature signatures;
    private final Clock clock;

    @Autowired
    TrustedClientIpFilter(@Value("${ygh.internal-request.hmac-base64}") String encodedSecret) {
        this(encodedSecret, Clock.systemUTC());
    }

    TrustedClientIpFilter(String encodedSecret, Clock clock) {
        this.clock = clock;
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("YGH_INTERNAL_REQUEST_HMAC_BASE64 must be valid Base64", malformed);
        }
        try {
            this.signatures = new InternalRequestSignature(secret, clock, Duration.ofSeconds(30));
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return Mono.error(new IllegalStateException("client remote address is unavailable"));
        }
        String clientIp = stripScope(remote.getAddress().getHostAddress());
        String traceId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID);
        String requestId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID);
        Instant timestamp = clock.instant();
        var metadata = new InternalRequestSignature.Metadata(
                clientIp, traceId, requestId, exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().pathWithinApplication().value(), timestamp);
        String signature = signatures.sign(metadata);
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(GatewayHeaders.CLIENT_IP);
            headers.remove(GatewayHeaders.CLIENT_IP_TIMESTAMP);
            headers.remove(GatewayHeaders.CLIENT_IP_SIGNATURE);
            headers.set(GatewayHeaders.CLIENT_IP, clientIp);
            headers.set(GatewayHeaders.CLIENT_IP_TIMESTAMP, Long.toString(timestamp.toEpochMilli()));
            headers.set(GatewayHeaders.CLIENT_IP_SIGNATURE, signature);
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 25; }

    private static String stripScope(String address) {
        int scope = address.indexOf('%');
        return scope < 0 ? address : address.substring(0, scope);
    }
}
