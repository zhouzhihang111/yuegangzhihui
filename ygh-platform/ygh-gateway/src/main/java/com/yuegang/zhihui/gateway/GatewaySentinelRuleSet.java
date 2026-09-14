package com.yuegang.zhihui.gateway;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import java.util.Set;

/** Creates the bounded route-level rules owned by the gateway. */
final class GatewaySentinelRuleSet {

    static final double MAX_QPS = 10_000;
    static final int MAX_BURST = 10_000;

    private GatewaySentinelRuleSet() {
    }

    static Set<GatewayFlowRule> create(
            double authQps, int authBurst, double serviceQps, int serviceBurst) {
        requireBoundedQps(authQps, "authQps");
        requireBoundedBurst(authBurst, "authBurst");
        requireBoundedQps(serviceQps, "serviceQps");
        requireBoundedBurst(serviceBurst, "serviceBurst");

        return Set.of(
                rule("auth-service", authQps, authBurst),
                rule("user-service", serviceQps, serviceBurst),
                rule("system-service", serviceQps, serviceBurst),
                rule("admin-service", serviceQps, serviceBurst));
    }

    private static GatewayFlowRule rule(String routeId, double qps, int burst) {
        return new GatewayFlowRule(routeId)
                .setCount(qps)
                .setIntervalSec(1)
                .setBurst(burst);
    }

    private static void requireBoundedQps(double value, String name) {
        if (!Double.isFinite(value) || value <= 0 || value > MAX_QPS) {
            throw new IllegalArgumentException(name + " must be greater than 0 and at most " + MAX_QPS);
        }
    }

    private static void requireBoundedBurst(int value, String name) {
        if (value < 0 || value > MAX_BURST) {
            throw new IllegalArgumentException(name + " must be between 0 and " + MAX_BURST);
        }
    }
}
