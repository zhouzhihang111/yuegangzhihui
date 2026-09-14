package com.yuegang.zhihui.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TokenResponse(
        @NotBlank String accessToken,
        @NotBlank String refreshToken,
        @NotBlank String tokenType,
        @Positive long expiresIn,
        @Positive long refreshExpiresIn) {
    @Override public String toString() { return "TokenResponse[tokenType=" + tokenType + ", tokens=[REDACTED]]"; }
}
