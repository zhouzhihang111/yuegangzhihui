package com.yuegang.zhihui.system.api;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import com.yuegang.zhihui.system.application.AiProviderConfigService;
import com.yuegang.zhihui.system.security.InternalServiceVerifier;
import com.yuegang.zhihui.system.security.SystemTrustedUserContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AiProviderConfigController {
    private final AiProviderConfigService service;
    private final SystemTrustedUserContextResolver users;
    private final InternalServiceVerifier internalServices;

    public AiProviderConfigController(AiProviderConfigService service,
                                      SystemTrustedUserContextResolver users,
                                      InternalServiceVerifier internalServices) {
        this.service = service;
        this.users = users;
        this.internalServices = internalServices;
    }

    @GetMapping("/api/v1/system/ai-provider-config")
    public ApiResponse<AiProviderConfigView> view(HttpServletRequest request) {
        require(request, "ai:config:read");
        return ApiResponse.success(service.view(), TraceIdResolver.resolve(request));
    }

    @PutMapping("/api/v1/system/ai-provider-config")
    public ApiResponse<AiProviderConfigView> update(@Valid @RequestBody UpdateAiProviderConfigRequest body,
                                                    HttpServletRequest request) {
        CurrentUserPrincipal principal = require(request, "ai:config:write");
        return ApiResponse.success(service.update(body, Long.parseLong(principal.userId())),
                TraceIdResolver.resolve(request));
    }

    @GetMapping("/internal/v1/system/ai-provider-config")
    public ApiResponse<InternalAiProviderConfig> internal(HttpServletRequest request) {
        String caller = request.getHeader("X-YGH-Service");
        if (!Set.of("ygh-ai-service", "ygh-search-service").contains(caller)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        internalServices.verify(request, caller);
        return ApiResponse.success(service.internal(), TraceIdResolver.resolve(request));
    }

    private CurrentUserPrincipal require(HttpServletRequest request, String permission) {
        CurrentUserPrincipal principal = users.resolve(request);
        if (!principal.roles().contains("ADMIN") && !principal.permissions().contains(permission)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return principal;
    }
}
