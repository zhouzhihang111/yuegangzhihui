package com.yuegang.zhihui.common.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successResponseCarriesStableEnvelopeFields() {
        var before = OffsetDateTime.now();

        var response = ApiResponse.success("payload", "trace-001");

        assertThat(response.code()).isEqualTo(ErrorCode.SUCCESS.code());
        assertThat(response.message()).isEqualTo("操作成功");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.traceId()).isEqualTo("trace-001");
        assertThat(response.timestamp()).isAfterOrEqualTo(before);
    }

    @Test
    void failureResponseDoesNotRequireBusinessData() {
        var response = ApiResponse.failure(ErrorCode.VALIDATION_ERROR, "参数错误", "trace-002");

        assertThat(response.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.message()).isEqualTo("参数错误");
        assertThat(response.data()).isNull();
        assertThat(response.traceId()).isEqualTo("trace-002");
    }
}
