package com.yuegang.zhihui.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Size(max = 190) String principal,
        @NotBlank @Size(max = 128) String captchaChallengeId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 32) String captchaAnswer) {
    @Override public String toString() { return "PasswordResetRequest[principal=" + principal + ", captchaAnswer=[REDACTED]]"; }
}
