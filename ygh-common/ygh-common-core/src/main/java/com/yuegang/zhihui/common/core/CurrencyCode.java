package com.yuegang.zhihui.common.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

/** Currencies supported by the current virtual-wallet release. */
public enum CurrencyCode implements StableCodeEnum {
    CNY("CNY", "人民币");

    private final String code;
    private final String displayName;

    CurrencyCode(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @JsonCreator
    public static CurrencyCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported currency code: " + code));
    }
}
