package com.yuegang.zhihui.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class SecurityErrorHandlerTest {

    @Test
    void authenticationEntryPointWritesTheStandard401EnvelopeWithoutExceptionDetails() throws Exception {
        var request = requestWithTraceId("trace-authentication");
        var response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint().commence(
                request,
                response,
                new BadCredentialsException("Bearer internal-secret-token is invalid"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"message\":\"未登录或登录已失效\"")
                .contains("\"traceId\":\"trace-authentication\"")
                .doesNotContain("internal-secret-token")
                .doesNotContain("BadCredentialsException");
    }

    @Test
    void accessDeniedHandlerWritesTheStandard403EnvelopeWithoutPolicyDetails() throws Exception {
        var request = requestWithTraceId("trace-access-denied");
        var response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler().handle(
                request,
                response,
                new AccessDeniedException("internal admin:wallet:write policy denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"code\":\"PERMISSION_DENIED\"")
                .contains("\"message\":\"无权执行该操作\"")
                .contains("\"traceId\":\"trace-access-denied\"")
                .doesNotContain("admin:wallet:write")
                .doesNotContain("AccessDeniedException");
    }

    private MockHttpServletRequest requestWithTraceId(String traceId) {
        var request = new MockHttpServletRequest();
        request.setAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE, traceId);
        return request;
    }
}
