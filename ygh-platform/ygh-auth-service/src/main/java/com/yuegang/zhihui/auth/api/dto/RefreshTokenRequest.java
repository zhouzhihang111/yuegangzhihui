package com.yuegang.zhihui.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 4096) String refreshToken,
        @Size(max = 128) String deviceId) {
    @Override public String toString() { return "RefreshTokenRequest[refreshToken=[REDACTED]]"; }
}
