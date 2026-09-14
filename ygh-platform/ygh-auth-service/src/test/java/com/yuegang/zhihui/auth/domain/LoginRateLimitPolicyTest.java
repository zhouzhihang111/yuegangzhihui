package com.yuegang.zhihui.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginRateLimitPolicyTest {

    @Test
    void freezesIndependentAccountAndIpWindows() {
        var policy = LoginRateLimitPolicy.enterpriseDefault();

        assertThat(policy.principalLimit()).isEqualTo(10);
        assertThat(policy.principalWindow()).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.ipLimit()).isEqualTo(30);
        assertThat(policy.ipWindow()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void rejectsNonPositiveAndUnreasonablyLargeLimits() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LoginRateLimitPolicy(0, Duration.ofMinutes(1), 10, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LoginRateLimitPolicy(10_001, Duration.ofMinutes(1), 10, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LoginRateLimitPolicy(10, Duration.ZERO, 10, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LoginRateLimitPolicy(10, Duration.ofNanos(999_999), 10, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new LoginRateLimitPolicy(10, Duration.ofDays(2), 10, Duration.ofMinutes(1)));
    }
}
