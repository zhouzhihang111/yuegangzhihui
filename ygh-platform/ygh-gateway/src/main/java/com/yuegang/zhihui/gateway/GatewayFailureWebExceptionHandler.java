package com.yuegang.zhihui.gateway;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/** Converts gateway infrastructure failures into the public error contract. */
@Component
final class GatewayFailureWebExceptionHandler implements WebExceptionHandler, Ordered {

    private final GatewaySecurityErrorWriter errorWriter;

    GatewayFailureWebExceptionHandler(GatewaySecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (BlockException.isBlockException(error)) {
            return errorWriter.rateLimited(exchange);
        }
        if (error instanceof NotFoundException) {
            return errorWriter.dependencyUnavailable(exchange);
        }
        return Mono.error(error);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
