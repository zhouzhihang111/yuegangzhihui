package com.yuegang.zhihui.auth.api;

import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.auth.application.AccountAdministrationService;
import com.yuegang.zhihui.auth.application.AuthAccountQueryService;
import com.yuegang.zhihui.common.core.*;
import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth/admin/users")
public final class AuthAdministrationController {
    private final AccountAdministrationService service; private final AuthAccountQueryService queries; private final AuthTrustedUserContextResolver users;
    public AuthAdministrationController(AccountAdministrationService service,AuthAccountQueryService queries,AuthTrustedUserContextResolver users){this.service=service;this.queries=queries;this.users=users;}
    @GetMapping public ApiResponse<java.util.List<AdminAccountView>> list(@RequestParam(required=false)String keyword,@RequestParam(required=false)String status,@RequestParam(defaultValue="50")int limit,HttpServletRequest request){admin(request);return ApiResponse.success(queries.list(keyword,status,limit),TraceIdResolver.resolve(request));}
    @PutMapping("/{userId}/status") public ApiResponse<AccountStatusResponse> change(@PathVariable String userId,@Valid @RequestBody ChangeAccountStatusRequest body,HttpServletRequest request){
        CurrentUserPrincipal operator=admin(request);
        return ApiResponse.success(service.change(userId,body,Long.parseLong(operator.userId())),TraceIdResolver.resolve(request));
    }
    private CurrentUserPrincipal admin(HttpServletRequest request){CurrentUserPrincipal operator=users.resolve(request);if(!operator.roles().contains("ADMIN"))throw new BusinessException(ErrorCode.PERMISSION_DENIED);return operator;}
}
