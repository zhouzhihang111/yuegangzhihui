package com.yuegang.zhihui.common.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ValueContractTest {

    @Test
    void externalIdKeepsValuesBeyondJavaScriptSafeIntegerAsText() {
        var id = ExternalId.of("9007199254740993");

        assertThat(id.value()).isEqualTo("9007199254740993");
        assertThat(id.toString()).isEqualTo("9007199254740993");
    }

    @Test
    void externalIdRejectsMissingOrSurroundedWhitespace() {
        assertThatThrownBy(() -> ExternalId.of(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExternalId.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExternalId.of(" user-1 "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moneyUsesExactBigDecimalScaleAndTheOnlySupportedCurrency() {
        var money = Money.cny(new BigDecimal("99.80"));

        assertThat(money.amount()).isEqualByComparingTo("99.80");
        assertThat(money.amount().scale()).isEqualTo(Money.SCALE);
        assertThat(money.currency()).isEqualTo(CurrencyCode.CNY);
        assertThat(money.currency().code()).isEqualTo("CNY");
        assertThat(CurrencyCode.values()).containsExactly(CurrencyCode.CNY);
    }

    @Test
    void moneyNeverRoundsOrChangesScaleImplicitly() {
        assertThatThrownBy(() -> Money.cny(new BigDecimal("99.8")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> Money.cny(new BigDecimal("99.801")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> Money.cny(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumCodesAreExplicitStableAndIndependentFromDisplayText() {
        assertThat(CurrencyCode.CNY.code()).isEqualTo("CNY");
        assertThat(CurrencyCode.CNY.displayName()).isEqualTo("人民币");
        assertThat(CurrencyCode.CNY.code()).isNotEqualTo(CurrencyCode.CNY.displayName());
    }
}
