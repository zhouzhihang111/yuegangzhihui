package com.yuegang.zhihui.common.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/** Exact non-negative monetary value. Signed ledger deltas use a separate domain type. */
public record Money(
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        CurrencyCode currency
) {

    public static final int SCALE = 2;

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.scale() != SCALE) {
            throw new IllegalArgumentException("amount scale must be exactly " + SCALE);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
    }

    public static Money cny(BigDecimal amount) {
        return new Money(amount, CurrencyCode.CNY);
    }
}
