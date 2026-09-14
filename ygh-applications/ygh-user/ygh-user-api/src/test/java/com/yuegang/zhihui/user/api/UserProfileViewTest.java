package com.yuegang.zhihui.user.api;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UserProfileViewTest {
    @Test void keepsIdsAsStringsAndRejectsUnsafeValues() {
        assertThat(new UserProfileView("42", "Alice", null, 0).userId()).isEqualTo("42");
        assertThatThrownBy(() -> new UserProfileView("0", "Alice", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
