package com.yuegang.zhihui.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 190) String principal,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 256) String password,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 256) String confirmPassword,
        @NotBlank @Size(max = 128) String captchaChallengeId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 32) String captchaAnswer,
        @AssertTrue boolean agreementAccepted) {
    @JsonIgnore @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordsMatching() { return password != null && password.equals(confirmPassword); }
    @Override public String toString() { return "RegisterRequest[principal=" + principal + ", secrets=[REDACTED]]"; }
}
