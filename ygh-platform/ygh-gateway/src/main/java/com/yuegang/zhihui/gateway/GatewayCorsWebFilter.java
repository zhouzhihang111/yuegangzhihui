package com.yuegang.zhihui.gateway;

import org.springframework.core.Ordered;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

/** Runs CORS before request guards so trusted browsers can read edge error envelopes. */
final class GatewayCorsWebFilter extends CorsWebFilter implements Ordered {

    GatewayCorsWebFilter(CorsConfigurationSource configurationSource) {
        super(configurationSource);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 12;
    }
}
