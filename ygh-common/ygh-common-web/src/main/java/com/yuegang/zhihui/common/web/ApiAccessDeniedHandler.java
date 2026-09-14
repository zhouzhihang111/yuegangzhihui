package com.yuegang.zhihui.common.web;

import com.yuegang.zhihui.common.core.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Standard JSON response for denied Servlet security requests. */
public final class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        SecurityApiResponseWriter.write(request, response, 403, ErrorCode.PERMISSION_DENIED);
    }
}
