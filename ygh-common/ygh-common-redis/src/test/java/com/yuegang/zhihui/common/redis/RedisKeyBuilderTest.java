package com.yuegang.zhihui.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisKeyBuilderTest {

    private final RedisKeyBuilder keys = new RedisKeyBuilder();

    @Test
    void buildsCanonicalNamespacedKey() {
        assertThat(keys.build("dev", "product", "detail", "10001"))
                .isEqualTo("ygh:dev:product:detail:10001");
        assertThat(keys.build("prod-cn", "auth", "blacklist", "01JZ_A.b-9"))
                .isEqualTo("ygh:prod-cn:auth:blacklist:01JZ_A.b-9");
    }

    @Test
    void rejectsAmbiguousOrDangerousSegments() {
        assertThatThrownBy(() -> keys.build("DEV", "auth", "captcha", "id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.build("dev", "auth:admin", "captcha", "id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.build("dev", "auth", "captcha", "{id}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.build("dev", "auth", "captcha", "../id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keys.build("dev", "auth", "captcha", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesOnlyCanonicalCompleteKeys() {
        assertThat(keys.isCanonical("ygh:dev:order:idempotency:req-1")).isTrue();
        assertThat(keys.isCanonical("other:dev:order:idempotency:req-1")).isFalse();
        assertThat(keys.isCanonical("ygh:dev:order:req-1")).isFalse();
        assertThat(keys.isCanonical("ygh:dev:order:idempotency:req:1")).isFalse();
    }

    @Test
    void hashesSensitiveIdentifiersWithoutLeakingInput() {
        var raw = "user@example.com";
        var identifier = RedisKeyIdentifier.sha256(raw);

        assertThat(identifier).matches("[0-9a-f]{64}").doesNotContain(raw);
        assertThat(RedisKeyIdentifier.sha256(raw)).isEqualTo(identifier);
        assertThatThrownBy(() -> RedisKeyIdentifier.sha256(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hmacProtectsLowEntropyIdentifiersWithSecretPepper() {
        var firstPepper = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var secondPepper = "abcdef0123456789abcdef0123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(RedisKeyIdentifier.hmacSha256("user@example.com", firstPepper))
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(RedisKeyIdentifier.hmacSha256("user@example.com", secondPepper));
        assertThatThrownBy(() -> RedisKeyIdentifier.hmacSha256("user@example.com", new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionKeysShareAccountHashTagForClusterLuaAtomicity() {
        var keys = new SessionRedisKeys(new RedisKeyBuilder(), "prod");

        assertThat(keys.session(42, "jwt-1")).contains("{42}");
        assertThat(keys.revoked(42, "jwt-1")).contains("{42}");
        assertThat(keys.accountState(42)).contains("{42}");
        assertThat(keys.session(43, "jwt-1")).contains("{43}").doesNotContain("{42}");
    }
}
