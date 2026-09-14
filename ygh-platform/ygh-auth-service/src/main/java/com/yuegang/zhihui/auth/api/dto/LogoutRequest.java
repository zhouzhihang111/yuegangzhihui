package com.yuegang.zhihui.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 4096) String refreshToken) {
    @Override public String toString() { return "LogoutRequest[refreshToken=[REDACTED]]"; }
}
