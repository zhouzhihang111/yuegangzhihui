package com.yuegang.zhihui.common.web;

import com.yuegang.zhihui.common.core.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Standard JSON response for unauthenticated Servlet security requests. */
public final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        SecurityApiResponseWriter.write(request, response, 401, ErrorCode.UNAUTHENTICATED);
    }
}
