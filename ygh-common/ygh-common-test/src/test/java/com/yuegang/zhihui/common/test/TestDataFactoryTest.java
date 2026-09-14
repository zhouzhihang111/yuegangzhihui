package com.yuegang.zhihui.common.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TestDataFactoryTest {

    @Test
    void generatesDeterministicReservedAndNonProductionIdentityData() {
        var first = new TestDataFactory(42L);
        var second = new TestDataFactory(42L);

        var user = first.nextUser();
        assertThat(user).isEqualTo(second.nextUser());
        assertThat(user.userId()).matches("test-user-42-1");
        assertThat(user.username()).isEqualTo("test_user_42_1");
        assertThat(user.email()).endsWith("@example.test");
        assertThat(user.displayName()).startsWith("测试用户");
        assertThat(user.toString()).doesNotContain(user.email());
    }

    @Test
    void sequenceIsUniqueWithinFactory() {
        var factory = new TestDataFactory(7L);
        assertThat(factory.nextUser().userId()).isNotEqualTo(factory.nextUser().userId());
        assertThat(factory.nextOrderId()).isNotEqualTo(factory.nextOrderId());
    }

    @Test
    void syntheticUserTypeRejectsProductionLikeIdentityData() {
        assertThatThrownBy(() -> new TestUserData(
                "42", "real_user", "person@company.com", "真实姓名"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
