package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.api.dto.AuthenticationResponse;
import com.yuegang.zhihui.auth.api.dto.CaptchaResponse;
import com.yuegang.zhihui.auth.api.dto.LoginRequest;
import com.yuegang.zhihui.auth.api.dto.LogoutRequest;
import com.yuegang.zhihui.auth.api.dto.OperationResponse;
import com.yuegang.zhihui.auth.api.dto.PasswordResetConfirmRequest;
import com.yuegang.zhihui.auth.api.dto.PasswordResetRequest;
import com.yuegang.zhihui.auth.api.dto.PasswordResetRequestedResponse;
import com.yuegang.zhihui.auth.api.dto.RefreshTokenRequest;
import com.yuegang.zhihui.auth.api.dto.RegisterRequest;
import com.yuegang.zhihui.auth.api.dto.TokenResponse;

/** Application boundary implemented incrementally by BE-0322 through BE-0325. */
public interface AuthCommandService {
    AuthenticationResponse register(RegisterRequest request);
    AuthenticationResponse login(LoginRequest request, LoginSecurityContext context);
    TokenResponse refresh(RefreshTokenRequest request);
    OperationResponse logout(LogoutRequest request, String authorization);
    CaptchaResponse captcha();
    PasswordResetRequestedResponse requestPasswordReset(PasswordResetRequest request);
    OperationResponse confirmPasswordReset(PasswordResetConfirmRequest request);
}
