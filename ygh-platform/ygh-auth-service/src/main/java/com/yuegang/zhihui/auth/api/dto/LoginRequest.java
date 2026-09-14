package com.yuegang.zhihui.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 190) String principal,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 256) String password,
        @Size(max = 128) String captchaChallengeId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 32) String captchaAnswer,
        @Size(max = 128) String deviceId) {
    @Override public String toString() { return "LoginRequest[principal=" + principal + ", secrets=[REDACTED]]"; }
}
