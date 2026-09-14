package com.yuegang.zhihui.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

class GatewayEdgeProtectionTest {

    private static final Set<String> UPLOAD_PATHS = Set.of(
            "/api/v1/knowledge/documents",
            "/api/v1/training/documents",
            "/api/v1/training/admin/chapters/*/documents",
            "/api/v1/products/images");

    @Test
    void corsUsesExactTrustedOriginsAndBoundedMethodsAndHeaders() {
        var source = GatewayCorsConfiguration.createSource(
                "https://mall.example.com,https://admin.example.com");
        var request = MockServerHttpRequest.options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "https://mall.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .build();
        var configuration = source.getCorsConfiguration(MockServerWebExchange.from(request));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://mall.example.com", "https://admin.example.com");
        assertThat(configuration.getAllowedOrigins()).doesNotContain("*");
        assertThat(configuration.getAllowedMethods()).containsExactly(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly(
                "Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id",
                "X-Trace-Id", "Accept-Language");
        assertThat(configuration.getExposedHeaders())
                .containsExactly("X-Request-Id", "X-Trace-Id", "Retry-After");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getMaxAge()).isEqualTo(3600);
    }

    @Test
    void corsRejectsWildcardMalformedAndEmptyOrigins() {
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource("*"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource("https://example.com/path"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource("https://user@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource("https://example.com?x=1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource("https://example.com#fragment"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource("https://example.com:99999"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource("https://[invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GatewayCorsConfiguration.createSource(" , "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guardRejectsOversizedContentLengthWithoutCallingDownstream() {
        var filter = guard(8, 16);
        var exchange = exchange(MockServerHttpRequest.post("/api/v1/orders")
                .contentLength(9)
                .body("123456789"));
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, ignored -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(calls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(413);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"VALIDATION_ERROR\"")
                .contains("\"traceId\":\"trace-edge-1234\"");
    }

    @Test
    void guardStopsChunkedBodyWhenCumulativeLimitIsExceeded() {
        var factory = DefaultDataBufferFactory.sharedInstance;
        var request = MockServerHttpRequest.post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Flux.just(factory.wrap(new byte[5]), factory.wrap(new byte[5])));
        var exchange = exchange(request);
        AtomicInteger calls = new AtomicInteger();

        guard(8, 16).filter(exchange, guarded ->
                Mono.fromRunnable(calls::incrementAndGet).then(
                DataBufferUtils.join(guarded.getRequest().getBody())
                        .doOnNext(DataBufferUtils::release)
                        .then())).block();

        assertThat(calls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(413);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"code\":\"VALIDATION_ERROR\"");
    }

    @Test
    void guardReplaysBoundedBodyForLegitimateSecondSubscription() {
        var request = MockServerHttpRequest.post("/api/v1/orders")
                .body("1234");
        var exchange = exchange(request);
        AtomicInteger calls = new AtomicInteger();

        guard(8, 16).filter(exchange, guarded -> {
            calls.incrementAndGet();
            return
                DataBufferUtils.join(guarded.getRequest().getBody())
                        .doOnNext(DataBufferUtils::release)
                        .then(DataBufferUtils.join(guarded.getRequest().getBody()))
                        .doOnNext(DataBufferUtils::release)
                        .then();
        }).block();

        assertThat(calls).hasValue(1);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void guardInvokesDownstreamExactlyOnceForEmptyUnknownLengthBody() {
        var request = MockServerHttpRequest.post("/api/v1/orders")
                .body(Flux.empty());
        var exchange = exchange(request);
        AtomicInteger calls = new AtomicInteger();

        guard(8, 16).filter(exchange, ignored -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(calls).hasValue(1);
    }

    @Test
    void multipartIsAllowedOnlyOnDeclaredUploadPathsAndUsesLargerLimit() {
        var denied = exchange(MockServerHttpRequest.post("/api/v1/orders/import")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body("1234"));
        AtomicInteger deniedCalls = new AtomicInteger();

        guard(8, 16).filter(denied, ignored -> {
            deniedCalls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(deniedCalls).hasValue(0);
        assertThat(denied.getResponse().getStatusCode().value()).isEqualTo(403);

        var nearMatch = exchange(MockServerHttpRequest.post("/api/v1/knowledge/documents/extra")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body("1234"));
        guard(8, 16).filter(nearMatch, ignored -> Mono.empty()).block();
        assertThat(nearMatch.getResponse().getStatusCode().value()).isEqualTo(403);

        var allowed = exchange(MockServerHttpRequest.post("/api/v1/knowledge/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .contentLength(12)
                .body("123456789012"));
        AtomicInteger allowedCalls = new AtomicInteger();
        guard(8, 16).filter(allowed, guarded -> {
            allowedCalls.incrementAndGet();
            return DataBufferUtils.join(guarded.getRequest().getBody())
                    .doOnNext(DataBufferUtils::release)
                    .then();
        }).block();

        assertThat(allowedCalls).hasValue(1);
        assertThat(allowed.getResponse().getStatusCode()).isNull();

        var dynamicTrainingUpload = exchange(MockServerHttpRequest
                .post("/api/v1/training/admin/chapters/5803040178821023063/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .contentLength(12)
                .body("123456789012"));
        AtomicInteger dynamicCalls = new AtomicInteger();
        guard(8, 16).filter(dynamicTrainingUpload, ignored -> {
            dynamicCalls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(dynamicCalls).hasValue(1);
        assertThat(dynamicTrainingUpload.getResponse().getStatusCode()).isNull();

        var unsafeExtraSegment = exchange(MockServerHttpRequest
                .post("/api/v1/training/admin/chapters/5803040178821023063/extra/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .contentLength(12)
                .body("123456789012"));
        guard(8, 16).filter(unsafeExtraSegment, ignored -> Mono.empty()).block();
        assertThat(unsafeExtraSegment.getResponse().getStatusCode().value()).isEqualTo(403);

        var chunkedUpload = exchange(MockServerHttpRequest.post("/api/v1/knowledge/documents")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body("1234"));
        guard(8, 16).filter(chunkedUpload, ignored -> Mono.empty()).block();
        assertThat(chunkedUpload.getResponse().getStatusCode().value()).isEqualTo(411);
    }

    @Test
    void guardRejectsUnsafeConfiguration() {
        var writer = new GatewaySecurityErrorWriter(new ObjectMapper());
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(0, 16, UPLOAD_PATHS, writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(
                GatewayRequestGuardFilter.MAX_CONFIGURABLE_BYTES + 1,
                GatewayRequestGuardFilter.MAX_CONFIGURABLE_BYTES + 1,
                UPLOAD_PATHS, writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(8, 7, UPLOAD_PATHS, writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(
                8, GatewayRequestGuardFilter.MAX_CONFIGURABLE_BYTES + 1, UPLOAD_PATHS, writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(8, 16, null, writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(8, 16, Set.of(), writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(8, 16, Set.of("relative"), writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(
                8, 16, Set.of("/api/v1/knowledge//documents"), writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(
                8, 16, Set.of("/api/v1/knowledge/documents/"), writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayRequestGuardFilter(
                8, 16, Set.of("/api/v1/knowledge/**"), writer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(GatewayEdgeConfiguration.parsePaths(" /one, ,/two "))
                .containsExactlyInAnyOrder("/one", "/two");
    }

    @Test
    void securityHeadersOverrideDownstreamAndRemoveServerDisclosure() {
        var exchange = exchange(MockServerHttpRequest.get("/api/v1/users/me").build());
        var filter = new GatewaySecurityHeadersFilter();

        filter.filter(exchange, ignored -> {
            exchange.getResponse().getHeaders().set("Server", "internal-version");
            exchange.getResponse().getHeaders().set("X-Frame-Options", "SAMEORIGIN");
            return exchange.getResponse().setComplete();
        }).block();

        assertThat(exchange.getResponse().getHeaders()).satisfies(headers -> {
            assertThat(headers.getFirst("Server")).isNull();
            assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
            assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(headers.getFirst("Cross-Origin-Resource-Policy")).isEqualTo("same-origin");
            assertThat(headers.getFirst("Permissions-Policy"))
                    .isEqualTo("camera=(), microphone=(), geolocation=()");
            assertThat(headers.getFirst("Content-Security-Policy"))
                    .isEqualTo("default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
            assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        });
    }

    @Test
    void securityHeadersPreserveExplicitCachingAndAddHstsOnlyForHttps() {
        var secure = exchange(MockServerHttpRequest.get("https://api.example.com/catalog").build());
        var filter = new GatewaySecurityHeadersFilter();

        filter.filter(secure, ignored -> {
            secure.getResponse().getHeaders().setCacheControl("public, max-age=60");
            return secure.getResponse().setComplete();
        }).block();

        assertThat(secure.getResponse().getHeaders().getCacheControl())
                .isEqualTo("public, max-age=60");
        assertThat(secure.getResponse().getHeaders().getFirst("Strict-Transport-Security"))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }

    private static GatewayRequestGuardFilter guard(long requestMax, long uploadMax) {
        return new GatewayRequestGuardFilter(requestMax, uploadMax, UPLOAD_PATHS,
                new GatewaySecurityErrorWriter(new ObjectMapper()));
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest request) {
        var exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewaySecurityAttributes.TRACE_ID, "trace-edge-1234");
        return exchange;
    }
}
