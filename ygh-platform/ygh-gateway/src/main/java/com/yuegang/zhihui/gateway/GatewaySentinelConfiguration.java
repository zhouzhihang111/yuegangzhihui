package com.yuegang.zhihui.gateway;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Installs static fail-fast gateway limits; dynamic rule persistence is configured separately. */
@Configuration(proxyBeanMethods = false)
class GatewaySentinelConfiguration {

    @Bean
    Set<GatewayFlowRule> gatewaySentinelRules(
            @Value("${ygh.gateway.sentinel.auth-qps:20}") double authQps,
            @Value("${ygh.gateway.sentinel.auth-burst:5}") int authBurst,
            @Value("${ygh.gateway.sentinel.service-qps:100}") double serviceQps,
            @Value("${ygh.gateway.sentinel.service-burst:10}") int serviceBurst) {
        Set<GatewayFlowRule> rules = GatewaySentinelRuleSet.create(
                authQps, authBurst, serviceQps, serviceBurst);
        GatewayRuleManager.loadRules(rules);
        return rules;
    }

    @Bean
    SentinelGatewayFilter sentinelGatewayFilter(Set<GatewayFlowRule> gatewaySentinelRules) {
        return new SentinelGatewayFilter(Ordered.HIGHEST_PRECEDENCE + 5);
    }
}
