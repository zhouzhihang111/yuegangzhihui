package com.yuegang.zhihui.auth.api;

import com.yuegang.zhihui.auth.infrastructure.RsaSigningKeyRing;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "ygh.security.jwt", name = "enabled", havingValue = "true")
public final class JwksController {
    private final RsaSigningKeyRing keyRing;

    public JwksController(RsaSigningKeyRing keyRing) { this.keyRing = keyRing; }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() { return keyRing.publicJwkSet(); }
}
