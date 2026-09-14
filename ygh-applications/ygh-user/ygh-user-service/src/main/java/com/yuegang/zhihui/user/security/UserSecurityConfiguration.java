package com.yuegang.zhihui.user.security;

import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
class UserSecurityConfiguration {
    @Bean TrustedUserContextResolver trustedUserContextResolver(
            @Value("${ygh.internal-request.hmac-base64}") String encodedSecret, Clock clock) {
        byte[] secret;
        try { secret = Base64.getDecoder().decode(encodedSecret); }
        catch (IllegalArgumentException malformed) { throw new IllegalStateException("internal request secret is malformed", malformed); }
        try { return new TrustedUserContextResolver(secret, clock); }
        finally { Arrays.fill(secret, (byte) 0); }
    }

    @Bean UserInternalServiceVerifier userInternalServiceVerifier(
            @Value("${ygh.internal-request.hmac-base64}") String encodedSecret) {
        byte[] secret = Base64.getDecoder().decode(encodedSecret);
        try { return new UserInternalServiceVerifier(secret); }
        finally { Arrays.fill(secret, (byte) 0); }
    }
}
