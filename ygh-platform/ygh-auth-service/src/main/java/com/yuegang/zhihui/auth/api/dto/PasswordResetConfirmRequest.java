package com.yuegang.zhihui.auth.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 4096) String resetToken,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 256) String newPassword,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 256) String confirmPassword) {
    @JsonIgnore @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordsMatching() { return newPassword != null && newPassword.equals(confirmPassword); }
    @Override public String toString() { return "PasswordResetConfirmRequest[secrets=[REDACTED]]"; }
}
