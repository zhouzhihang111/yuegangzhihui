package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class GatewayContextFilterTest {

    @Test
    void correlationFilterPreservesValidIdsAndRemovesSpoofedIdentityHeaders() {
        var filter = new CorrelationIdFilter(() -> "unused-generated-id");
        var request = MockServerHttpRequest.get("/api/v1/auth/session")
                .header(GatewayHeaders.TRACE_ID, "trace-client-1234")
                .header(GatewayHeaders.REQUEST_ID, "request-client-1234")
                .header(GatewayHeaders.USER_ID, "forged-admin")
                .header(GatewayHeaders.ROLES, "ADMIN")
                .header(GatewayHeaders.PERMISSIONS, "wallet:balance:write")
                .build();
        var exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capturingWebChain(downstream)).block();

        HttpHeaders downstreamHeaders = downstream.get().getRequest().getHeaders();
        assertThat(downstreamHeaders.getFirst(GatewayHeaders.TRACE_ID))
                .isEqualTo("trace-client-1234");
        assertThat(downstreamHeaders.getFirst(GatewayHeaders.REQUEST_ID))
                .isEqualTo("request-client-1234");
        assertNoIdentityHeaders(downstreamHeaders);
        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayHeaders.TRACE_ID))
                .isEqualTo("trace-client-1234");
        assertThat(exchange.getResponse().getHeaders().getFirst(GatewayHeaders.REQUEST_ID))
                .isEqualTo("request-client-1234");
        assertThat(downstream.get().<String>getAttribute(GatewaySecurityAttributes.TRACE_ID))
                .isEqualTo("trace-client-1234");
        assertThat(downstream.get().<String>getAttribute(GatewaySecurityAttributes.REQUEST_ID))
                .isEqualTo("request-client-1234");
    }

    @Test
    void correlationFilterReplacesMalformedOrOversizedIds() {
        var generated = new ArrayDeque<>(List.of(
                "generated-trace-1234", "generated-request-1234"));
        var filter = new CorrelationIdFilter(generated::removeFirst);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header(GatewayHeaders.TRACE_ID, "bad id with spaces")
                .header(GatewayHeaders.REQUEST_ID, "x".repeat(65))
                .build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capturingWebChain(downstream)).block();

        assertThat(downstream.get().getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID))
                .isEqualTo("generated-trace-1234");
        assertThat(downstream.get().getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID))
                .isEqualTo("generated-request-1234");
    }

    @Test
    void correlationFilterGeneratesIdsWhenHeadersAreAbsent() {
        var generated = new ArrayDeque<>(List.of(
                "generated-trace-5678", "generated-request-5678"));
        var filter = new CorrelationIdFilter(generated::removeFirst);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login").build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capturingWebChain(downstream)).block();

        assertThat(downstream.get().getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID))
                .isEqualTo("generated-trace-5678");
        assertThat(downstream.get().getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID))
                .isEqualTo("generated-request-5678");
    }

    @Test
    void correlationFilterNormalizesConflictingResponseHeadersBeforeCommit() {
        var filter = new CorrelationIdFilter(() -> "unused-generated-id");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header(GatewayHeaders.TRACE_ID, "trace-canonical-1234")
                .header(GatewayHeaders.REQUEST_ID, "request-canonical-1234")
                .build());

        filter.filter(exchange, downstream -> {
            downstream.getResponse().getHeaders().add(GatewayHeaders.TRACE_ID, "downstream-trace-one");
            downstream.getResponse().getHeaders().add(GatewayHeaders.TRACE_ID, "downstream-trace-two");
            downstream.getResponse().getHeaders().set(GatewayHeaders.REQUEST_ID, "downstream-request");
            return downstream.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getHeaders().get(GatewayHeaders.TRACE_ID))
                .containsExactly("trace-canonical-1234");
        assertThat(exchange.getResponse().getHeaders().get(GatewayHeaders.REQUEST_ID))
                .containsExactly("request-canonical-1234");
    }

    @Test
    void correlationFilterRejectsUnsafeGeneratorOutput() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login").build());

        assertThatThrownBy(() -> new CorrelationIdFilter(() -> null)
                .filter(exchange, ignored -> Mono.empty()).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsafe value");
        assertThatThrownBy(() -> new CorrelationIdFilter(() -> "bad")
                .filter(exchange, ignored -> Mono.empty()).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsafe value");
    }

    @Test
    void trustedContextFilterInjectsOnlyServerAuthenticatedPrincipal() {
        var filter = new TrustedUserContextFilter();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header(GatewayHeaders.TRACE_ID, "trace-123456")
                .header(GatewayHeaders.REQUEST_ID, "request-123456")
                .header(GatewayHeaders.USER_ID, "forged-user")
                .header(GatewayHeaders.ROLES, "SUPER_ADMIN")
                .build());
        exchange.getAttributes().put(GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL,
                new CurrentUserPrincipal(
                        "user-9007199254740993",
                        Set.of("CUSTOMER", "EMPLOYEE"),
                        Set.of("user:profile:read", "training:course:learn")));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capturingGatewayChain(downstream)).block();

        HttpHeaders headers = downstream.get().getRequest().getHeaders();
        assertThat(headers.getFirst(GatewayHeaders.USER_ID)).isEqualTo("user-9007199254740993");
        assertThat(headers.getFirst(GatewayHeaders.ROLES)).isEqualTo("CUSTOMER,EMPLOYEE");
        assertThat(headers.getFirst(GatewayHeaders.PERMISSIONS))
                .isEqualTo("training:course:learn,user:profile:read");
        assertThat(headers.getFirst(GatewayHeaders.USER_CONTEXT_TIMESTAMP)).isNotBlank();
        assertThat(headers.getFirst(GatewayHeaders.USER_CONTEXT_SIGNATURE)).matches("[0-9a-f]{64}");
    }

    @Test
    void trustedContextFilterLeavesNoIdentityHeadersWithoutAuthenticatedPrincipal() {
        var filter = new TrustedUserContextFilter();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login")
                .header(GatewayHeaders.USER_ID, "forged-user")
                .header(GatewayHeaders.ROLES, "ADMIN")
                .header(GatewayHeaders.PERMISSIONS, "system:permission:grant")
                .build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capturingGatewayChain(downstream)).block();

        assertNoIdentityHeaders(downstream.get().getRequest().getHeaders());
    }

    @Test
    void trustedContextFilterOmitsEmptyAuthorityHeaders() {
        var filter = new TrustedUserContextFilter();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header(GatewayHeaders.TRACE_ID, "trace-123456")
                .header(GatewayHeaders.REQUEST_ID, "request-123456").build());
        exchange.getAttributes().put(GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL,
                new CurrentUserPrincipal("user-1", Set.of(), Set.of()));
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capturingGatewayChain(downstream)).block();

        HttpHeaders headers = downstream.get().getRequest().getHeaders();
        assertThat(headers.getFirst(GatewayHeaders.USER_ID)).isEqualTo("user-1");
        assertThat(headers.getFirst(GatewayHeaders.ROLES)).isNull();
        assertThat(headers.getFirst(GatewayHeaders.PERMISSIONS)).isNull();
    }

    @Test
    void trustedContextFilterFailsClosedForHeaderInjectionCharacters() {
        var filter = new TrustedUserContextFilter();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());
        exchange.getAttributes().put(GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL,
                new CurrentUserPrincipal("user-1\r\nX-Forged:true", Set.of("CUSTOMER"), Set.of()));

        assertThatThrownBy(() -> filter.filter(exchange, ignored -> Mono.empty()).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted userId");
    }

    @Test
    void trustedContextFilterRejectsUnsafeOrUnboundedAuthorities() {
        var filter = new TrustedUserContextFilter();

        assertThatThrownBy(() -> filterWithPrincipal(filter, new CurrentUserPrincipal(
                "user-1", Set.of("ADMIN,ROOT"), Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe authority");

        Set<String> tooManyRoles = IntStream.range(0, 129)
                .mapToObj(index -> "ROLE_" + index)
                .collect(Collectors.toSet());
        assertThatThrownBy(() -> filterWithPrincipal(filter, new CurrentUserPrincipal(
                "user-1", tooManyRoles, Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count limit");

        Set<String> oversizedRoles = IntStream.range(0, 40)
                .mapToObj(index -> "ROLE_" + index + "_" + "X".repeat(110))
                .collect(Collectors.toSet());
        assertThatThrownBy(() -> filterWithPrincipal(filter, new CurrentUserPrincipal(
                "user-1", oversizedRoles, Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header length limit");
    }

    @Test
    void jwtBridgePublishesOnlyAuthenticatedJwtPrincipal() {
        var filter = new JwtPrincipalBridgeFilter(new JwtPrincipalMapper());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        Jwt jwt = gatewayJwt();
        var authentication = new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());

        filter.filter(exchange, countingGatewayChain(downstream, calls))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .block();

        assertThat(calls).hasValue(1);
        assertThat(downstream.get().<CurrentUserPrincipal>getAttribute(
                GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL))
                .isEqualTo(new CurrentUserPrincipal(
                        "user-1001", Set.of("CUSTOMER"), Set.of("user:profile:read")));
    }

    @Test
    void jwtBridgeDoesNotTrustMissingOrNonJwtSecurityContext() {
        var filter = new JwtPrincipalBridgeFilter(new JwtPrincipalMapper());
        var withoutContext = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());
        AtomicReference<ServerWebExchange> firstDownstream = new AtomicReference<>();
        AtomicInteger firstCalls = new AtomicInteger();

        filter.filter(withoutContext, countingGatewayChain(firstDownstream, firstCalls)).block();

        assertThat(firstCalls).hasValue(1);
        assertThat(firstDownstream.get().<CurrentUserPrincipal>getAttribute(
                GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL)).isNull();

        var nonJwt = UsernamePasswordAuthenticationToken.authenticated(
                "user-1001", "not-used", List.of());
        var nonJwtExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());
        AtomicReference<ServerWebExchange> secondDownstream = new AtomicReference<>();
        AtomicInteger secondCalls = new AtomicInteger();

        filter.filter(nonJwtExchange, countingGatewayChain(secondDownstream, secondCalls))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(nonJwt))
                .block();

        assertThat(secondCalls).hasValue(1);
        assertThat(secondDownstream.get().<CurrentUserPrincipal>getAttribute(
                GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL)).isNull();

        var unauthenticated = new UsernamePasswordAuthenticationToken("user-1001", "not-used");
        var unauthenticatedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());
        AtomicReference<ServerWebExchange> thirdDownstream = new AtomicReference<>();
        AtomicInteger thirdCalls = new AtomicInteger();

        filter.filter(unauthenticatedExchange, countingGatewayChain(thirdDownstream, thirdCalls))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthenticated))
                .block();

        assertThat(thirdCalls).hasValue(1);
        assertThat(thirdDownstream.get().<CurrentUserPrincipal>getAttribute(
                GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL)).isNull();
    }

    @Test
    void securityErrorWriterDoesNothingAfterResponseCommit() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());
        exchange.getResponse().setComplete().block();
        var writer = new GatewaySecurityErrorWriter(new tools.jackson.databind.ObjectMapper());

        writer.unauthenticated(exchange).block();

        assertThat(exchange.getResponse().isCommitted()).isTrue();
    }

    @Test
    void jwtSessionValidationAllowsActiveAndFailsClosedForRevokedOrUnavailableState() {
        var writer = new GatewaySecurityErrorWriter(new tools.jackson.databind.ObjectMapper());
        Jwt jwt = gatewayJwt();
        var authentication = new JwtAuthenticationToken(jwt, List.of(), jwt.getSubject());

        AtomicInteger activeCalls = new AtomicInteger();
        var active = new JwtSessionValidationFilter((accountId, jwtId) -> {
            assertThat(accountId).isEqualTo(7);
            assertThat(jwtId).isEqualTo("jwt-session-1");
            return Mono.just(true);
        }, writer);
        active.filter(
                        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build()),
                        ignored -> { activeCalls.incrementAndGet(); return Mono.empty(); })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block();
        assertThat(activeCalls).hasValue(1);

        AtomicInteger revokedCalls = new AtomicInteger();
        var revokedExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());
        new JwtSessionValidationFilter((ignoredAccount, ignoredJti) -> Mono.just(false), writer)
                .filter(revokedExchange, ignored -> { revokedCalls.incrementAndGet(); return Mono.empty(); })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block();
        assertThat(revokedCalls).hasValue(0);
        assertThat(revokedExchange.getResponse().getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);

        var unavailableExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());
        new JwtSessionValidationFilter((ignoredAccount, ignoredJti) -> Mono.error(new IllegalStateException("redis")), writer)
                .filter(unavailableExchange, ignored -> Mono.empty())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block();
        assertThat(unavailableExchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);

        var downstreamFailure = new JwtSessionValidationFilter(
                (ignoredAccount, ignoredJti) -> Mono.just(true), writer);
        assertThatThrownBy(() -> downstreamFailure.filter(
                        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build()),
                        ignored -> Mono.error(new IllegalArgumentException("downstream")))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("downstream");
    }

    @Test
    void filtersHaveStableOrderAroundFutureAuthenticationFilter() {
        assertThat(new CorrelationIdFilter().getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
        assertThat(new JwtPrincipalBridgeFilter(new JwtPrincipalMapper()).getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 20);
        assertThat(new JwtSessionValidationFilter((account, jwt) -> Mono.just(true),
                new GatewaySecurityErrorWriter(new tools.jackson.databind.ObjectMapper())).getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 15);
        assertThat(new TrustedUserContextFilter().getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 30);
    }

    private static WebFilterChain capturingWebChain(AtomicReference<ServerWebExchange> captured) {
        return exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
    }

    private static GatewayFilterChain capturingGatewayChain(AtomicReference<ServerWebExchange> captured) {
        return exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
    }

    private static GatewayFilterChain countingGatewayChain(
            AtomicReference<ServerWebExchange> captured,
            AtomicInteger calls
    ) {
        return exchange -> {
            calls.incrementAndGet();
            captured.set(exchange);
            return Mono.empty();
        };
    }

    private static void assertNoIdentityHeaders(HttpHeaders headers) {
        assertThat(headers.getFirst(GatewayHeaders.USER_ID)).isNull();
        assertThat(headers.getFirst(GatewayHeaders.ROLES)).isNull();
        assertThat(headers.getFirst(GatewayHeaders.PERMISSIONS)).isNull();
    }

    private static void filterWithPrincipal(
            TrustedUserContextFilter filter,
            CurrentUserPrincipal principal
    ) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me").build());
        exchange.getAttributes().put(GatewaySecurityAttributes.AUTHENTICATED_PRINCIPAL, principal);
        filter.filter(exchange, ignored -> Mono.empty()).block();
    }

    private static Jwt gatewayJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("opaque-test-value")
                .header("alg", "RS256")
                .subject("user-1001")
                .issuer("https://auth.example.test")
                .audience(List.of("ygh-api"))
                .issuedAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(60))
                .claim("roles", List.of("CUSTOMER"))
                .claim("permissions", List.of("user:profile:read"))
                .claim("account_id", "7")
                .claim("jti", "jwt-session-1")
                .build();
    }
}
