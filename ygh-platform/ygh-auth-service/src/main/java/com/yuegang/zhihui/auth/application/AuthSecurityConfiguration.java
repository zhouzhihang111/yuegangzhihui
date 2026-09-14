package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.domain.AccountLockPolicy;
import com.yuegang.zhihui.auth.domain.AccountSecurityRepository;
import com.yuegang.zhihui.auth.domain.Argon2PasswordHasher;
import com.yuegang.zhihui.auth.domain.PasswordPolicy;
import com.yuegang.zhihui.auth.domain.RefreshTokenRepository;
import com.yuegang.zhihui.auth.domain.LoginAttemptRepository;
import com.yuegang.zhihui.auth.domain.LoginAccountRepository;
import com.yuegang.zhihui.auth.domain.LoginRateLimitPolicy;
import com.yuegang.zhihui.auth.domain.LoginRateLimiter;
import com.yuegang.zhihui.auth.domain.SensitiveValueHasher;
import com.yuegang.zhihui.auth.domain.AccessTokenIssuer;
import com.yuegang.zhihui.auth.domain.PasswordDigest;
import com.yuegang.zhihui.auth.infrastructure.ClasspathCompromisedPasswordChecker;
import com.yuegang.zhihui.auth.infrastructure.JdbcAccountSecurityRepository;
import com.yuegang.zhihui.auth.infrastructure.JdbcRefreshTokenRepository;
import com.yuegang.zhihui.auth.infrastructure.JdbcLoginAttemptRepository;
import com.yuegang.zhihui.auth.infrastructure.JdbcLoginAccountRepository;
import com.yuegang.zhihui.auth.infrastructure.RedisLoginRateLimiter;
import com.yuegang.zhihui.auth.infrastructure.RedisCaptchaChallengeStore;
import com.yuegang.zhihui.common.redis.RedisKeyBuilder;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import com.yuegang.zhihui.common.security.InternalRequestSignature;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import com.yuegang.zhihui.auth.api.AuthTrustedUserContextResolver;
import com.yuegang.zhihui.auth.domain.AccountAdministrationRepository;
import com.yuegang.zhihui.auth.infrastructure.JdbcAccountAdministrationRepository;
import com.yuegang.zhihui.auth.infrastructure.HttpSystemAuthorityProvider;
import com.yuegang.zhihui.auth.domain.AuthorityProvider;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration(proxyBeanMethods = false)
class AuthSecurityConfiguration {

    @Bean
    PasswordPolicy passwordPolicy() {
        return new PasswordPolicy(
                PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH,
                new ClasspathCompromisedPasswordChecker());
    }

    @Bean
    Argon2PasswordHasher argon2PasswordHasher() {
        return Argon2PasswordHasher.owaspMinimum();
    }

    @Bean
    AccountLockPolicy accountLockPolicy() {
        return new AccountLockPolicy(5, Duration.ofMinutes(15));
    }

    @Bean
    AccountSecurityRepository accountSecurityRepository(DataSource dataSource) {
        return new JdbcAccountSecurityRepository(dataSource);
    }

    @Bean AccountAdministrationRepository accountAdministrationRepository(DataSource dataSource){return new JdbcAccountAdministrationRepository(dataSource);}
    @Bean AccountAdministrationService accountAdministrationService(AccountAdministrationRepository repository,SessionStateStore sessions){return new AccountAdministrationService(repository,sessions);}
    @Bean AuthAccountQueryService authAccountQueryService(DataSource dataSource){return new AuthAccountQueryService(dataSource);}
    @Bean AuthTrustedUserContextResolver authTrustedUserContextResolver(
            @org.springframework.beans.factory.annotation.Value("${ygh.internal-request.hmac-base64}") String encoded,Clock clock){
        byte[] secret=Base64.getDecoder().decode(encoded);try{return new AuthTrustedUserContextResolver(secret,clock);}finally{Arrays.fill(secret,(byte)0);}
    }
    @Bean @ConditionalOnProperty(prefix="ygh.security.jwt",name="enabled",havingValue="true") AuthorityProvider authorityProvider(
            @org.springframework.beans.factory.annotation.Value("${ygh.system.internal-base-url}") String base,
            @org.springframework.beans.factory.annotation.Value("${ygh.internal-request.hmac-base64}") String encoded,Clock clock){byte[] secret=Base64.getDecoder().decode(encoded);try{return new HttpSystemAuthorityProvider(base,secret,clock);}finally{Arrays.fill(secret,(byte)0);}}

    @Bean
    RefreshTokenRepository refreshTokenRepository(DataSource dataSource) {
        return new JdbcRefreshTokenRepository(dataSource);
    }

    @Bean
    OpaqueRefreshTokenService opaqueRefreshTokenService(
            RefreshTokenRepository repository,
            Clock clock,
            @org.springframework.beans.factory.annotation.Value("${ygh.security.jwt.refresh-token-days:14}") long lifetimeDays) {
        return new OpaqueRefreshTokenService(repository, clock, Duration.ofDays(lifetimeDays));
    }

    @Bean
    AccountLockService accountLockService(
            AccountSecurityRepository repository,
            AccountLockPolicy policy,
            Clock clock) {
        return new AccountLockService(repository, policy, clock);
    }

    @Bean
    SensitiveValueHasher sensitiveValueHasher(
            @org.springframework.beans.factory.annotation.Value("${ygh.auth.audit-pepper-base64}")
            String encodedPepper) {
        byte[] pepper;
        try {
            pepper = Base64.getDecoder().decode(encodedPepper);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("YGH_AUTH_AUDIT_PEPPER_BASE64 must be valid Base64", malformed);
        }
        try {
            return new SensitiveValueHasher(pepper);
        } finally {
            Arrays.fill(pepper, (byte) 0);
        }
    }

    @Bean
    InternalRequestSignature internalRequestSignature(
            @org.springframework.beans.factory.annotation.Value("${ygh.internal-request.hmac-base64}")
            String encodedSecret,
            Clock clock) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("YGH_INTERNAL_REQUEST_HMAC_BASE64 must be valid Base64", malformed);
        }
        try {
            return new InternalRequestSignature(secret, clock, Duration.ofSeconds(30));
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    TrustedClientContextResolver trustedClientContextResolver(InternalRequestSignature signatures) {
        return new TrustedClientContextResolver(signatures);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "false", matchIfMissing = true)
    TrustedClientContextResolver directClientContextResolver() {
        return TrustedClientContextResolver.directForTests();
    }

    @Bean
    LoginRateLimitPolicy loginRateLimitPolicy(
            @org.springframework.beans.factory.annotation.Value("${ygh.auth.rate-limit.principal-limit:10}")
            int principalLimit,
            @org.springframework.beans.factory.annotation.Value("${ygh.auth.rate-limit.principal-window:15m}")
            Duration principalWindow,
            @org.springframework.beans.factory.annotation.Value("${ygh.auth.rate-limit.ip-limit:30}")
            int ipLimit,
            @org.springframework.beans.factory.annotation.Value("${ygh.auth.rate-limit.ip-window:15m}")
            Duration ipWindow) {
        return new LoginRateLimitPolicy(principalLimit, principalWindow, ipLimit, ipWindow);
    }

    @Bean
    LoginAttemptRepository loginAttemptRepository(DataSource dataSource) {
        return new JdbcLoginAttemptRepository(dataSource);
    }

    @Bean
    LoginAccountRepository loginAccountRepository(DataSource dataSource) {
        return new JdbcLoginAccountRepository(dataSource);
    }

    @Bean
    LoginAuditService loginAuditService(
            LoginAttemptRepository repository, SensitiveValueHasher hasher, Clock clock) {
        return new LoginAuditService(repository, hasher, clock);
    }

    @Bean
    LoginRateLimiter loginRateLimiter(
            StringRedisTemplate redis,
            RedisKeyBuilder keys,
            SensitiveValueHasher hasher,
            LoginRateLimitPolicy policy,
            @org.springframework.beans.factory.annotation.Value("${ygh.redis.environment}") String environment) {
        return new RedisLoginRateLimiter(redis, keys, hasher, policy, environment);
    }

    @Bean
    PasswordDigest dummyLoginPasswordDigest(Argon2PasswordHasher hasher) {
        char[] dummy = "non-account timing equalizer 2026".toCharArray();
        try {
            return hasher.hash(dummy);
        } finally {
            Arrays.fill(dummy, '\0');
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    LoginUseCase loginUseCase(
            LoginAccountRepository accounts,
            LoginRateLimiter rateLimiter,
            AccountLockService accountLocks,
            Argon2PasswordHasher passwordHasher,
            PasswordDigest dummyLoginPasswordDigest,
            AccessTokenIssuer accessTokens,
            OpaqueRefreshTokenService refreshTokens,
            LoginAuditService audit,
            SessionStateStore sessions,
            Clock clock) {
        return new LoginUseCase(accounts, rateLimiter, accountLocks, passwordHasher,
                dummyLoginPasswordDigest, accessTokens, refreshTokens, audit, sessions, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    RedisCaptchaChallengeStore captchaChallengeStore(StringRedisTemplate redis, RedisKeyBuilder keys,
            @org.springframework.beans.factory.annotation.Value("${ygh.redis.environment}") String environment) {
        return new RedisCaptchaChallengeStore(redis, keys, environment);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    CaptchaService captchaService(RedisCaptchaChallengeStore store, SensitiveValueHasher hasher, Clock clock) {
        return new CaptchaService(store, hasher, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    SnowflakeIdGenerator authIdGenerator(
            @org.springframework.beans.factory.annotation.Value("${ygh.auth.id-worker}") long workerId, Clock clock) {
        return new SnowflakeIdGenerator(workerId, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    RegistrationUseCase registrationUseCase(LoginAccountRepository accounts, PasswordPolicy passwords,
            Argon2PasswordHasher passwordHasher, CaptchaService captchas, SnowflakeIdGenerator ids,
            AccessTokenIssuer accessTokens, OpaqueRefreshTokenService refreshTokens,
            SessionStateStore sessions, Clock clock) {
        return new RegistrationUseCase(accounts, passwords, passwordHasher, captchas, ids,
                accessTokens, refreshTokens, sessions, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    TokenLifecycleUseCase tokenLifecycleUseCase(OpaqueRefreshTokenService refreshTokens,
            LoginAccountRepository accounts, AccessTokenIssuer accessTokens,
            SessionStateStore sessions, Clock clock) {
        return new TokenLifecycleUseCase(refreshTokens, accounts, accessTokens, sessions, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    PasswordResetNotificationClient passwordResetNotificationClient(
            @org.springframework.beans.factory.annotation.Value("${ygh.notification.internal-base-url}") String baseUrl,
            @org.springframework.beans.factory.annotation.Value("${ygh.internal-request.hmac-base64}") String encoded,
            Clock clock) {
        byte[] secret = Base64.getDecoder().decode(encoded);
        try { return new PasswordResetNotificationClient(baseUrl, secret, clock); }
        finally { Arrays.fill(secret, (byte) 0); }
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    PasswordResetService passwordResetService(LoginAccountRepository accounts, CaptchaService captchas,
            PasswordPolicy passwordPolicy, Argon2PasswordHasher passwordHasher,
            PasswordResetNotificationClient notifications, DataSource dataSource, Clock clock) {
        return new PasswordResetService(accounts, captchas, passwordPolicy, passwordHasher,
                notifications, dataSource, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    PasswordChangeService passwordChangeService(LoginAccountRepository accounts, PasswordPolicy passwordPolicy,
            Argon2PasswordHasher passwordHasher, DataSource dataSource, SessionStateStore sessions, Clock clock) {
        return new PasswordChangeService(accounts, passwordPolicy, passwordHasher, dataSource, sessions, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
    AuthCommandService operationalAuthCommandService(LoginUseCase loginUseCase,
            RegistrationUseCase registrationUseCase, TokenLifecycleUseCase tokenLifecycle,
            CaptchaService captchas, AccessTokenVerificationService accessTokenVerifier,
            PasswordResetService passwordResetService) {
        return new OperationalAuthCommandService(loginUseCase, registrationUseCase, tokenLifecycle,
                captchas, accessTokenVerifier, passwordResetService);
    }
}
