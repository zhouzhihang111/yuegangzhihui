package com.yuegang.zhihui.common.redis;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ReactiveSessionValidator {
    Mono<Boolean> valid(long accountId, String jwtId);
}
