package com.yuegang.zhihui.auth.api;

import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.auth.application.AuthCommandService;
import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.web.TraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import com.yuegang.zhihui.auth.application.TrustedClientContextResolver;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController {
    private final AuthCommandService authService;
    private final TrustedClientContextResolver clientContextResolver;

    public AuthController(AuthCommandService authService) {
        this(authService, TrustedClientContextResolver.directForTests());
    }

    @Autowired
    public AuthController(AuthCommandService authService, TrustedClientContextResolver clientContextResolver) {
        this.authService = authService;
        this.clientContextResolver = clientContextResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(authService.register(request), servletRequest));
    }

    @PostMapping("/login")
    public ApiResponse<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return success(authService.login(request, clientContextResolver.resolve(servletRequest)), servletRequest);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        return success(authService.refresh(request), servletRequest);
    }

    @PostMapping("/logout")
    public ApiResponse<OperationResponse> logout(
            @Valid @RequestBody LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest servletRequest) {
        return success(authService.logout(request, authorization), servletRequest);
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha(HttpServletRequest servletRequest) {
        return success(authService.captcha(), servletRequest);
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<PasswordResetRequestedResponse>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.accepted()
                .body(success(authService.requestPasswordReset(request), servletRequest));
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<OperationResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest servletRequest) {
        return success(authService.confirmPasswordReset(request), servletRequest);
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, TraceIdResolver.resolve(request));
    }

}
