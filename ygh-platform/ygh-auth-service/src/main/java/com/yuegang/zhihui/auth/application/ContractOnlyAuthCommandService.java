package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.api.dto.*;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;

/** Fail-closed placeholder until the corresponding authentication use cases are implemented. */
final class ContractOnlyAuthCommandService implements AuthCommandService {
    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Override public AuthenticationResponse register(RegisterRequest request) { throw unavailable(); }
    @Override public AuthenticationResponse login(LoginRequest request, LoginSecurityContext context) { throw unavailable(); }
    @Override public TokenResponse refresh(RefreshTokenRequest request) { throw unavailable(); }
    @Override public OperationResponse logout(LogoutRequest request, String authorization) { throw unavailable(); }
    @Override public CaptchaResponse captcha() { throw unavailable(); }
    @Override public PasswordResetRequestedResponse requestPasswordReset(PasswordResetRequest request) {
        throw unavailable();
    }
    @Override public OperationResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        throw unavailable();
    }
}
