package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import java.util.Objects;

/** Operational authentication adapter. */
final class OperationalAuthCommandService implements AuthCommandService {
    private final LoginUseCase loginUseCase;
    private final RegistrationUseCase registrationUseCase;
    private final TokenLifecycleUseCase tokenLifecycle;
    private final CaptchaService captchas;
    private final AccessTokenVerificationService accessTokenVerifier;
    private final PasswordResetService passwordResets;

    OperationalAuthCommandService(LoginUseCase loginUseCase, RegistrationUseCase registrationUseCase,
            TokenLifecycleUseCase tokenLifecycle, CaptchaService captchas,
            AccessTokenVerificationService accessTokenVerifier, PasswordResetService passwordResets) {
        this.loginUseCase = Objects.requireNonNull(loginUseCase, "loginUseCase must not be null");
        this.registrationUseCase = Objects.requireNonNull(registrationUseCase);
        this.tokenLifecycle = Objects.requireNonNull(tokenLifecycle);
        this.captchas = Objects.requireNonNull(captchas);
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier);
        this.passwordResets = Objects.requireNonNull(passwordResets);
    }

    @Override public AuthenticationResponse login(LoginRequest request, LoginSecurityContext context) {
        return loginUseCase.login(request, context);
    }

    @Override public AuthenticationResponse register(RegisterRequest request) { return registrationUseCase.register(request); }
    @Override public TokenResponse refresh(RefreshTokenRequest request) { return tokenLifecycle.refresh(request); }
    @Override public OperationResponse logout(LogoutRequest request, String authorization) {
        return tokenLifecycle.logout(request, accessTokenVerifier.verifyAuthorization(authorization));
    }
    @Override public CaptchaResponse captcha() { return captchas.create(); }
    @Override public PasswordResetRequestedResponse requestPasswordReset(PasswordResetRequest request) {
        return passwordResets.request(request);
    }
    @Override public OperationResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        return passwordResets.confirm(request);
    }
}
