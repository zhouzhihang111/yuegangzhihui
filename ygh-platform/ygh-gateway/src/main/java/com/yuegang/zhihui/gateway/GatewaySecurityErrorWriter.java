package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.core.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/** Writes credential-free security failures using the common response envelope. */
@Component
final class GatewaySecurityErrorWriter {

    private final ObjectMapper objectMapper;

    GatewaySecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Mono<Void> unauthenticated(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
    }

    Mono<Void> accessDenied(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, ErrorCode.PERMISSION_DENIED);
    }

    Mono<Void> rateLimited(ServerWebExchange exchange) {
        if (!exchange.getResponse().isCommitted()) {
            exchange.getResponse().getHeaders().set("Retry-After", "1");
        }
        return write(exchange, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED);
    }

    Mono<Void> dependencyUnavailable(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    Mono<Void> payloadTooLarge(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.VALIDATION_ERROR);
    }

    Mono<Void> uploadPathRejected(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, ErrorCode.PERMISSION_DENIED);
    }

    Mono<Void> lengthRequired(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.LENGTH_REQUIRED, ErrorCode.VALIDATION_ERROR);
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        String traceId = exchange.getAttributeOrDefault(
                GatewaySecurityAttributes.TRACE_ID, "unavailable");
        byte[] body = objectMapper.writeValueAsBytes(
                ApiResponse.failure(errorCode, null, traceId));
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(body)));
    }
}
