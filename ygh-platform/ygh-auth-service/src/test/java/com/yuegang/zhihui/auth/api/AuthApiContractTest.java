package com.yuegang.zhihui.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuegang.zhihui.auth.application.AuthCommandService;
import com.yuegang.zhihui.auth.api.dto.AuthenticationResponse;
import com.yuegang.zhihui.auth.api.dto.CaptchaResponse;
import com.yuegang.zhihui.auth.api.dto.OperationResponse;
import com.yuegang.zhihui.auth.api.dto.PasswordResetRequestedResponse;
import com.yuegang.zhihui.auth.api.dto.TokenResponse;
import com.yuegang.zhihui.common.web.GlobalExceptionHandler;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthApiContractTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(new StubAuthService()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void exposesTheCompleteVersionedAuthenticationContract() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-Id", "request-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principal":"user@example.test","password":"Password123!",
                                 "confirmPassword":"Password123!","captchaChallengeId":"captcha-1",
                                 "captchaAnswer":"a7k9","agreementAccepted":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userId").value("10001"))
                .andExpect(jsonPath("$.data.tokens.accessToken").value("access-token"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principal":"user@example.test","password":"Password123!",
                                 "captchaChallengeId":"captcha-1","captchaAnswer":"a7k9",
                                 "deviceId":"device-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("10001"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"refreshToken\":\"refresh-token\"," +
                                "\"deviceId\":\"device-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        mvc.perform(get("/api/v1/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").value("captcha-1"))
                .andExpect(jsonPath("$.data.imageBase64").exists());

        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"principal":"user@example.test","captchaChallengeId":"captcha-1",
                                 "captchaAnswer":"a7k9"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.expiresIn").value(600))
                .andExpect(jsonPath("$.data.accepted").doesNotExist());

        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetToken":"reset-token","newPassword":"NewPassword123!",
                                 "confirmPassword":"NewPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    @Test
    void validationErrorsNeverEchoSecrets() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", "request-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principal\":\"\",\"password\":\"secret-value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").value("request-invalid"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-value"))));
    }

    @Test
    void unavailableBusinessImplementationFailsClosedWithStable503Envelope() throws Exception {
        var unavailableService = new StubAuthService() {
            @Override
            public AuthenticationResponse login(com.yuegang.zhihui.auth.api.dto.LoginRequest request,
                    com.yuegang.zhihui.auth.application.LoginSecurityContext context) {
                throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            }
        };
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(unavailableService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", "request-unavailable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principal\":\"user\",\"password\":\"secret\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value("request-unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    private static class StubAuthService implements AuthCommandService {
        private static TokenResponse tokens() {
            return new TokenResponse("access-token", "refresh-token", "Bearer", 900, 604800);
        }

        @Override
        public AuthenticationResponse register(com.yuegang.zhihui.auth.api.dto.RegisterRequest request) {
            return new AuthenticationResponse("10001", tokens());
        }

        @Override
        public AuthenticationResponse login(com.yuegang.zhihui.auth.api.dto.LoginRequest request,
                com.yuegang.zhihui.auth.application.LoginSecurityContext context) {
            return new AuthenticationResponse("10001", tokens());
        }

        @Override
        public TokenResponse refresh(com.yuegang.zhihui.auth.api.dto.RefreshTokenRequest request) {
            return tokens();
        }

        @Override
        public OperationResponse logout(com.yuegang.zhihui.auth.api.dto.LogoutRequest request, String authorization) {
            return new OperationResponse(true);
        }

        @Override
        public CaptchaResponse captcha() {
            return new CaptchaResponse("captcha-1", "image/png", "aW1hZ2U=", OffsetDateTime.now().plusMinutes(3));
        }

        @Override
        public PasswordResetRequestedResponse requestPasswordReset(
                com.yuegang.zhihui.auth.api.dto.PasswordResetRequest request) {
            return new PasswordResetRequestedResponse(600);
        }

        @Override
        public OperationResponse confirmPasswordReset(
                com.yuegang.zhihui.auth.api.dto.PasswordResetConfirmRequest request) {
            return new OperationResponse(true);
        }
    }
}
