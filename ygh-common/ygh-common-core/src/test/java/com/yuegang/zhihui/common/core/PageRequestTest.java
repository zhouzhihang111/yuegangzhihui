package com.yuegang.zhihui.common.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    void defaultsArePageOneAndTwentyRows() {
        assertThat(PageRequest.defaults()).isEqualTo(new PageRequest(1, 20));
    }

    @Test
    void rejectsPageSizeAboveOneHundred() {
        assertThatThrownBy(() -> new PageRequest(1, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
    }

    @Test
    void calculatesZeroBasedOffset() {
        assertThat(new PageRequest(3, 20).offset()).isEqualTo(40L);
    }
}
