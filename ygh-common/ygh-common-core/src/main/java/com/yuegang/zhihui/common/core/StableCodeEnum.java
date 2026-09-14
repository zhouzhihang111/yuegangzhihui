package com.yuegang.zhihui.common.core;

import com.fasterxml.jackson.annotation.JsonValue;

/** Enum contract whose external value is independent from Java constant names. */
public interface StableCodeEnum {

    @JsonValue
    String code();

    String displayName();
}
