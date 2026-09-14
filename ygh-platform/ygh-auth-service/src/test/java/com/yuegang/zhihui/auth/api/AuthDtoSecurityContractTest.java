package com.yuegang.zhihui.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.auth.api.dto.LoginRequest;
import com.yuegang.zhihui.auth.api.dto.LogoutRequest;
import com.yuegang.zhihui.auth.api.dto.PasswordResetConfirmRequest;
import com.yuegang.zhihui.auth.api.dto.PasswordResetRequest;
import com.yuegang.zhihui.auth.api.dto.RefreshTokenRequest;
import com.yuegang.zhihui.auth.api.dto.RegisterRequest;
import com.yuegang.zhihui.auth.api.dto.TokenResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AuthDtoSecurityContractTest {

    @Test
    void secretFieldsAreWriteOnlyAndRedactedFromDiagnosticStrings() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var login = mapper.readValue("""
                {"principal":"user@example.test","password":"Secret123!",
                 "captchaChallengeId":"captcha-1","captchaAnswer":"answer","deviceId":"device-1"}
                """, LoginRequest.class);

        assertThat(login.password()).isEqualTo("Secret123!");
        assertThat(login.toString()).doesNotContain("Secret123!", "answer");
        assertThat(mapper.writeValueAsString(login))
                .doesNotContain("Secret123!", "answer", "password", "captchaAnswer");
    }

    @Test
    void passwordConfirmationIsValidatedAtTheDtoBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new PasswordResetConfirmRequest(
                    "reset-token", "NewPassword123!", "different-password"));
            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("passwordsMatching");
        }
    }

    @Test
    void everyCredentialBearingDtoRedactsDiagnosticOutput() {
        assertThat(new RegisterRequest(
                "user", "password-secret", "password-secret", "c", "answer-secret", true).toString())
                .doesNotContain("password-secret", "answer-secret");
        assertThat(new RefreshTokenRequest("refresh-secret", "device").toString())
                .doesNotContain("refresh-secret");
        assertThat(new LogoutRequest("logout-secret").toString()).doesNotContain("logout-secret");
        assertThat(new PasswordResetRequest("user", "captcha", "answer-secret").toString())
                .doesNotContain("answer-secret");
        assertThat(new PasswordResetConfirmRequest("reset-secret", "p1", "p1").toString())
                .doesNotContain("reset-secret", "p1");
        assertThat(new TokenResponse("access-secret", "refresh-secret", "Bearer", 1, 1).toString())
                .doesNotContain("access-secret", "refresh-secret");
    }
}
