package com.yuegang.zhihui.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    @Test
    void reusesIncomingCorrelationIdsAndRecordsOnlySafeRequestMetadata() throws Exception {
        var events = new ArrayList<RequestLogEvent>();
        var filter = new RequestLoggingFilter(events::add);
        var request = sensitiveRequest();
        request.setAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE, "trace-existing");
        request.addHeader(TraceIdResolver.REQUEST_ID_HEADER, "request-existing");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            ((MockHttpServletResponse) servletResponse).setStatus(201);
            ((MockHttpServletResponse) servletResponse)
                    .addHeader("Set-Cookie", "SESSION=server-secret-cookie");
        });

        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.traceId()).isEqualTo("trace-existing");
        assertThat(event.requestId()).isEqualTo("request-existing");
        assertThat(event.method()).isEqualTo("POST");
        assertThat(event.path()).isEqualTo("/api/v1/orders");
        assertThat(event.path()).doesNotContain("?");
        assertThat(event.status()).isEqualTo(201);
        assertThat(event.durationMs()).isNotNegative();
        assertThat(event.headers().keySet()).allMatch("User-Agent"::equals);
        assertThat(event.headers().values())
                .allSatisfy(value -> assertThat(value)
                        .doesNotContainIgnoringCase("token")
                        .doesNotContainIgnoringCase("secret"));
        assertNoSensitiveData(event);
    }

    @Test
    void generatesMissingTraceAndRequestIdsAndMakesThemAvailableDownstream() throws Exception {
        var events = new ArrayList<RequestLogEvent>();
        var filter = new RequestLoggingFilter(events::add);
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        var response = new MockHttpServletResponse();
        var downstreamTraceIds = new ArrayList<String>();
        var downstreamRequestIds = new ArrayList<String>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            downstreamTraceIds.add((String) servletRequest.getAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE));
            downstreamRequestIds.add((String) servletRequest.getAttribute(TraceIdResolver.REQUEST_ID_ATTRIBUTE));
        });

        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.traceId()).isNotBlank().isNotEqualTo("unavailable");
        assertThat(event.requestId()).isNotBlank().isNotEqualTo("unavailable");
        assertThat(downstreamTraceIds).containsExactly(event.traceId());
        assertThat(downstreamRequestIds).containsExactly(event.requestId());
    }

    @Test
    void neverConsumesOrBuffersTheRequestBodyForLogging() throws Exception {
        var events = new ArrayList<RequestLogEvent>();
        var filter = new RequestLoggingFilter(events::add);
        var request = sensitiveRequest();
        var response = new MockHttpServletResponse();
        var downstreamBodies = new ArrayList<String>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> downstreamBodies.add(
                new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(downstreamBodies).containsExactly(
                "{\"password\":\"PlainSecret-123\",\"address\":\"广东省广州市天河区完整地址88号\"}");
        assertThat(events).hasSize(1);
        assertNoSensitiveData(events.getFirst());
    }

    @Test
    void recordsCompletionAndRethrowsTheOriginalFailureWithoutLoggingItsMessage() {
        var events = new ArrayList<RequestLogEvent>();
        var filter = new RequestLoggingFilter(events::add);
        var request = sensitiveRequest();
        var response = new MockHttpServletResponse();
        var failure = new ServletException("database password and bearer token leaked");

        assertThatThrownBy(() -> filter.doFilter(
                        request,
                        response,
                        (servletRequest, servletResponse) -> {
                            throw failure;
                        }))
                .isSameAs(failure);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().status()).isEqualTo(500);
        assertThat(events.getFirst().durationMs()).isNotNegative();
        assertNoSensitiveData(events.getFirst());
    }

    @Test
    void sinkFailureNeverChangesAnOtherwiseSuccessfulResponse() {
        var filter = new RequestLoggingFilter(event -> {
            throw new IllegalStateException("logging backend unavailable");
        });
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        var response = new MockHttpServletResponse();

        assertThatCode(() -> filter.doFilter(request, response, (servletRequest, servletResponse) ->
                        ((MockHttpServletResponse) servletResponse).setStatus(204)))
                .doesNotThrowAnyException();

        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void sinkFailureNeverOverridesTheOriginalChainFailure() {
        var filter = new RequestLoggingFilter(event -> {
            throw new IllegalStateException("logging backend unavailable");
        });
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        var response = new MockHttpServletResponse();
        var originalFailure = new ServletException("original service failure");

        assertThatThrownBy(() -> filter.doFilter(
                        request,
                        response,
                        (servletRequest, servletResponse) -> {
                            throw originalFailure;
                        }))
                .isSameAs(originalFailure);
    }

    @Test
    void rejectsUnsafeOrOversizedIncomingCorrelationIdsAndGeneratesSafeOnes() throws Exception {
        var unsafeValues = List.of(
                "bad\r\nX-Forged: true",
                "bad\u0000control",
                "x".repeat(129),
                "spaces are not allowed",
                "slash/is/not/allowed");

        for (var unsafeValue : unsafeValues) {
            var events = new ArrayList<RequestLogEvent>();
            var filter = new RequestLoggingFilter(events::add);
            var request = new MockHttpServletRequest("GET", "/api/v1/products");
            request.setAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE, unsafeValue);
            request.addHeader(TraceIdResolver.REQUEST_ID_HEADER, unsafeValue);

            filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            });

            assertThat(events).hasSize(1);
            assertSafeGeneratedIdentifier(events.getFirst().traceId(), unsafeValue);
            assertSafeGeneratedIdentifier(events.getFirst().requestId(), unsafeValue);
        }
    }

    @Test
    void acceptsOnlyTheDocumentedSafeCorrelationIdAlphabet() throws Exception {
        var events = new ArrayList<RequestLogEvent>();
        var filter = new RequestLoggingFilter(events::add);
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE, "trace.AZ_az-09");
        request.addHeader(TraceIdResolver.REQUEST_ID_HEADER, "request.AZ_az-09");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
        });

        assertThat(events.getFirst().traceId()).isEqualTo("trace.AZ_az-09");
        assertThat(events.getFirst().requestId()).isEqualTo("request.AZ_az-09");
    }

    @Test
    void reusesSafeTraceHeaderWhenNoTrustedAttributeExists() throws Exception {
        var events = new ArrayList<RequestLogEvent>();
        var filter = new RequestLoggingFilter(events::add);
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader(TraceIdResolver.TRACE_ID_HEADER, "gateway-trace-01");
        request.setAttribute(TraceIdResolver.REQUEST_ID_ATTRIBUTE, "request-attribute-01");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
        });

        assertThat(events.getFirst().traceId()).isEqualTo("gateway-trace-01");
        assertThat(events.getFirst().requestId()).isEqualTo("request-attribute-01");
    }

    @Test
    void userAgentRemovesControlCharactersAndIsBoundedTo256Characters() throws Exception {
        var events = new ArrayList<RequestLogEvent>();
        var filter = new RequestLoggingFilter(events::add);
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("User-Agent", "Enterprise\u0000Browser\r\n" + "x".repeat(300));

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
        });

        var sanitizedUserAgent = events.getFirst().headers().get("User-Agent");
        assertThat(sanitizedUserAgent).hasSizeLessThanOrEqualTo(256);
        assertThat(sanitizedUserAgent).matches("[^\\p{Cntrl}]*");
    }

    @Test
    void userAgentContainingSensitiveKeywordsIsRedactedAsAWholeValue() throws Exception {
        var events = new ArrayList<RequestLogEvent>();
        var filter = new RequestLoggingFilter(events::add);
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("User-Agent", "EnterpriseBrowser token=credential");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
        });

        assertThat(events.getFirst().headers()).containsEntry("User-Agent", "[REDACTED]");
    }

    private MockHttpServletRequest sensitiveRequest() {
        var request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.setQueryString("token=query-secret&address=广东省广州市天河区完整地址88号");
        request.setContentType("application/json");
        request.setContent(
                "{\"password\":\"PlainSecret-123\",\"address\":\"广东省广州市天河区完整地址88号\"}"
                        .getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer authorization-secret-token");
        request.addHeader("Cookie", "SESSION=request-secret-cookie");
        request.addHeader("X-Password", "header-password");
        request.addHeader("X-Api-Token", "header-token");
        request.addHeader("X-Secret", "header-secret");
        request.addHeader("User-Agent", "EnterpriseBrowser/1.0 token=must-be-redacted");
        return request;
    }

    private void assertNoSensitiveData(RequestLogEvent event) {
        var rendered = event.toString();
        assertThat(rendered)
                .doesNotContainIgnoringCase("authorization")
                .doesNotContainIgnoringCase("cookie")
                .doesNotContainIgnoringCase("set-cookie")
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("token")
                .doesNotContainIgnoringCase("secret")
                .doesNotContain("广东省广州市天河区完整地址88号")
                .doesNotContain("PlainSecret-123");
    }

    private void assertSafeGeneratedIdentifier(String identifier, String unsafeValue) {
        assertThat(identifier)
                .isNotEqualTo(unsafeValue)
                .hasSizeLessThanOrEqualTo(128)
                .matches("[A-Za-z0-9._-]+");
    }
}
