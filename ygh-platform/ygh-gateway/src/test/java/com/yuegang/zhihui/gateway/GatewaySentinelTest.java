package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@ResourceLock("sentinel-gateway-rules")
class GatewaySentinelTest {

    private Set<GatewayFlowRule> originalRules;

    @BeforeEach
    void preserveGlobalRules() {
        originalRules = Set.copyOf(GatewayRuleManager.getRules());
    }

    @AfterEach
    void restoreGlobalRules() {
        GatewayRuleManager.loadRules(originalRules);
    }

    @Test
    void ruleSetDefinesBoundedPerRouteQpsAndBurst() {
        Set<GatewayFlowRule> rules = GatewaySentinelRuleSet.create(20, 5, 100, 10);
        var byResource = rules.stream().collect(Collectors.toMap(
                GatewayFlowRule::getResource, Function.identity()));

        assertThat(byResource).containsOnlyKeys(
                "auth-service", "user-service", "system-service", "admin-service");
        assertThat(byResource.get("auth-service").getCount()).isEqualTo(20);
        assertThat(byResource.get("auth-service").getBurst()).isEqualTo(5);
        assertThat(byResource.values()).allSatisfy(rule -> {
            assertThat(rule.getIntervalSec()).isEqualTo(1);
            assertThat(rule.getCount()).isPositive();
            assertThat(rule.getBurst()).isNotNegative();
        });
        assertThat(byResource.get("user-service").getCount()).isEqualTo(100);
        assertThat(byResource.get("system-service").getCount()).isEqualTo(100);
        assertThat(byResource.get("admin-service").getCount()).isEqualTo(100);
    }

    @Test
    void ruleSetRejectsNonPositiveQpsOrNegativeBurst() {
        assertThatThrownBy(() -> GatewaySentinelRuleSet.create(0, 0, 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authQps");
        assertThatThrownBy(() -> GatewaySentinelRuleSet.create(20, -1, 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authBurst");
        assertThatThrownBy(() -> GatewaySentinelRuleSet.create(20, 5, -1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceQps");
        assertThatThrownBy(() -> GatewaySentinelRuleSet.create(Double.POSITIVE_INFINITY, 5, 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authQps");
        assertThatThrownBy(() -> GatewaySentinelRuleSet.create(
                GatewaySentinelRuleSet.MAX_QPS + 1, 5, 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authQps");
        assertThatThrownBy(() -> GatewaySentinelRuleSet.create(20, 5, Double.NaN, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceQps");
        assertThatThrownBy(() -> GatewaySentinelRuleSet.create(
                20, GatewaySentinelRuleSet.MAX_BURST + 1, 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authBurst");
        assertThatThrownBy(() -> GatewaySentinelRuleSet.create(20, 5, 100, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceBurst");
    }

    @Test
    void sentinelGatewayFilterBlocksRouteWithZeroCapacityRule() {
        String routeId = "sentinel-test-route";
        GatewayRuleManager.loadRules(Set.of(new GatewayFlowRule(routeId)
                .setCount(0)
                .setIntervalSec(1)
                .setBurst(0)));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/test").build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async()
                        .id(routeId)
                        .uri(URI.create("http://example.test"))
                        .predicate(ignored -> true)
                        .build());
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> new SentinelGatewayFilter()
                .filter(exchange, ignored -> Mono.fromRunnable(calls::incrementAndGet))
                .block())
                .satisfies(error -> assertThat(BlockException.isBlockException(
                        Exceptions.unwrap(error))).isTrue());
        assertThat(calls).hasValue(0);
    }

    @Test
    void failureHandlerWritesRecognizableRateLimitAndDependencyResponses() {
        var writer = new GatewaySecurityErrorWriter(new ObjectMapper());
        var handler = new GatewayFailureWebExceptionHandler(writer);
        var limited = exchange("trace-rate-1234");

        handler.handle(limited, new FlowException("blocked")).block();

        assertThat(limited.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(limited.getResponse().getBodyAsString().block())
                .contains("\"code\":\"RATE_LIMITED\"")
                .contains("\"traceId\":\"trace-rate-1234\"");

        var unavailable = exchange("trace-dependency-1234");
        handler.handle(unavailable, new NotFoundException("No service instance")).block();

        assertThat(unavailable.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getResponse().getBodyAsString().block())
                .contains("\"code\":\"DEPENDENCY_UNAVAILABLE\"")
                .contains("\"traceId\":\"trace-dependency-1234\"");
    }

    @Test
    void failureHandlerPropagatesUnknownErrors() {
        var handler = new GatewayFailureWebExceptionHandler(
                new GatewaySecurityErrorWriter(new ObjectMapper()));
        var failure = new IllegalStateException("test failure");

        assertThatThrownBy(() -> handler.handle(exchange("trace-error-1234"), failure).block())
                .isSameAs(failure);
    }

    private static MockServerWebExchange exchange(String traceId) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());
        exchange.getAttributes().put(GatewaySecurityAttributes.TRACE_ID, traceId);
        return exchange;
    }
}
