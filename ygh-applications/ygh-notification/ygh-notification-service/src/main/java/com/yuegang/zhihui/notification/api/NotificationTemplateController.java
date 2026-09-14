package com.yuegang.zhihui.notification.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.notification.application.NotificationTemplateService;
import com.yuegang.zhihui.notification.security.NotificationSecurity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications/templates")
public final class NotificationTemplateController {
    private final NotificationTemplateService service;
    private final NotificationSecurity security;

    public NotificationTemplateController(NotificationTemplateService service, NotificationSecurity security) {
        this.service = service;
        this.security = security;
    }

    @GetMapping
    ApiResponse<List<NotificationTemplateView>> list(HttpServletRequest request) {
        security.require(request, "notification:template:write");
        return ApiResponse.success(service.list(), TraceIdResolver.resolve(request));
    }

    @PutMapping("/{code}")
    ApiResponse<NotificationTemplateView> save(@PathVariable String code,
            @Valid @RequestBody SaveNotificationTemplateRequest body, HttpServletRequest request) {
        security.require(request, "notification:template:write");
        return ApiResponse.success(service.save(code, body), TraceIdResolver.resolve(request));
    }
}
