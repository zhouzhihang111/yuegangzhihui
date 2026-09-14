package com.yuegang.zhihui.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuditLoggingFilterTest {
    private final AuditLoggingFilter filter = new AuditLoggingFilter();

    @Test
    void onlyMutationMethodsAreAudited() {
        for (String method : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            var request = new MockHttpServletRequest(method, "/api/v1/resource");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }
        for (String method : new String[]{"GET", "HEAD", "OPTIONS"}) {
            var request = new MockHttpServletRequest(method, "/api/v1/resource");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }
    }

    @Test
    void mutationCompletesWithoutReadingBodyOrCredentials() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("X-YGH-User-Id", "123456");
        request.setAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE, "trace-audit");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(201);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    void anonymousAndMalformedUserIdentifiersRemainSafe() throws Exception {
        for (String user : new String[]{null, "not-a-number", "123456789012345678901"}) {
            var request = new MockHttpServletRequest("DELETE", "/internal/v1/resource");
            if (user != null) request.addHeader("X-YGH-User-Id", user);
            filter.doFilterInternal(request, new MockHttpServletResponse(), (req, res) -> { });
        }
    }

    @Test
    void failedMutationIsRethrownAfterAudit() {
        var request = new MockHttpServletRequest("PATCH", "/api/v1/resource");
        var response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request, response,
                (req, res) -> { throw new IllegalStateException("synthetic failure"); }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic failure");
    }
}
