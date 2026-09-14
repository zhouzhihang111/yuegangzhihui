package com.yuegang.zhihui.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TtlJitterPolicyTest {

    @Test
    void appliesBoundedSymmetricJitter() {
        var base = Duration.ofMinutes(10);

        assertThat(new TtlJitterPolicy(0.10d, () -> 0.0d).cacheTtl(base))
                .isEqualTo(Duration.ofMinutes(9));
        assertThat(new TtlJitterPolicy(0.10d, () -> 0.5d).cacheTtl(base))
                .isEqualTo(base);
        assertThat(new TtlJitterPolicy(0.10d, () -> Math.nextDown(1.0d)).cacheTtl(base))
                .isBetween(base, Duration.ofMinutes(11));
    }

    @Test
    void neverReturnsZeroOrExceedsConfiguredBounds() {
        var policy = new TtlJitterPolicy(0.50d, () -> 0.0d);
        assertThat(policy.cacheTtl(Duration.ofMillis(1))).isEqualTo(Duration.ofMillis(1));

        assertThatThrownBy(() -> policy.cacheTtl(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.cacheTtl(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.cacheTtl(TtlJitterPolicy.MAX_BASE_TTL.plusMillis(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.cacheTtl(Duration.ofSeconds(Long.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverShortensSecurityOrIdempotencyRetention() {
        var minimum = Duration.ofHours(24);
        assertThat(new TtlJitterPolicy(0.10d, () -> 0.0d).minimumRetentionTtl(minimum))
                .isEqualTo(minimum);
        assertThat(new TtlJitterPolicy(0.10d, () -> Math.nextDown(1.0d))
                .minimumRetentionTtl(minimum))
                .isBetween(minimum, Duration.ofMinutes(1584));
    }

    @Test
    void rejectsUnsafeRatioAndBrokenRandomSource() {
        assertThatThrownBy(() -> new TtlJitterPolicy(-0.01d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TtlJitterPolicy(0.51d))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TtlJitterPolicy(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TtlJitterPolicy(0.10d, () -> 1.0d)
                .cacheTtl(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
