package com.yuegang.zhihui.auth.application;

import com.yuegang.zhihui.auth.domain.AccessTokenIssuer;
import com.yuegang.zhihui.auth.domain.AuthorityProvider;
import com.yuegang.zhihui.auth.infrastructure.NimbusAccessTokenIssuer;
import com.yuegang.zhihui.auth.infrastructure.RsaSigningKeyRing;
import com.yuegang.zhihui.common.redis.SessionStateStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
class JwtTokenConfiguration {
    @Bean
    RsaSigningKeyRing rsaSigningKeyRing(
            @Value("${ygh.security.jwt.key-directory}") String directory,
            @Value("${ygh.security.jwt.active-kid}") String activeKid) {
        return RsaSigningKeyRing.load(Path.of(directory), activeKid);
    }

    @Bean
    AccessTokenIssuer accessTokenIssuer(
            RsaSigningKeyRing keyRing,
            @Value("${ygh.security.jwt.issuer}") String issuer,
            @Value("${ygh.security.jwt.audience:ygh-api}") String audience,
            @Value("${ygh.security.jwt.access-token-minutes:15}") long lifetimeMinutes,
            SessionStateStore sessions,
            Clock clock, AuthorityProvider authorities) {
        var signer = new NimbusAccessTokenIssuer(
                keyRing, issuer, audience, Duration.ofMinutes(lifetimeMinutes), clock);
        return new AuthorityAwareAccessTokenIssuer(new SessionAwareAccessTokenIssuer(signer, sessions, clock), authorities);
    }

    @Bean
    AccessTokenVerificationService accessTokenVerificationService(RsaSigningKeyRing keyRing,
            @Value("${ygh.security.jwt.issuer}") String issuer,
            @Value("${ygh.security.jwt.audience:ygh-api}") String audience, Clock clock) {
        return new AccessTokenVerificationService(keyRing, issuer, audience, clock);
    }
}
