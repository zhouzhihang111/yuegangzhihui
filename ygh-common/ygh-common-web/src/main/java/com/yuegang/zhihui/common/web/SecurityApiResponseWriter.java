package com.yuegang.zhihui.common.web;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.core.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

/** Writes sanitized security failures before a request reaches Spring MVC. */
final class SecurityApiResponseWriter {

    private static final JsonMapper MAPPER = YghJacksonConfiguration.createMapper();

    private SecurityApiResponseWriter() {
    }

    static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        var body = ApiResponse.<Void>failure(
                errorCode,
                errorCode.defaultMessage(),
                TraceIdResolver.resolve(request));
        MAPPER.writeValue(response.getOutputStream(), body);
    }
}
