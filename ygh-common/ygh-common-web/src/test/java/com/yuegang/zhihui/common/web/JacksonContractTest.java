package com.yuegang.zhihui.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuegang.zhihui.common.core.CurrencyCode;
import com.yuegang.zhihui.common.core.ExternalId;
import com.yuegang.zhihui.common.core.Money;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JacksonContractTest {

    private final JsonMapper mapper = YghJacksonConfiguration.createMapper();

    @Test
    void externalIdRoundTripsAsAJsonStringRatherThanANumberOrObject() throws Exception {
        var id = ExternalId.of("9007199254740993");

        var json = mapper.writeValueAsString(id);

        assertThat(json).isEqualTo("\"9007199254740993\"");
        assertThat(mapper.readValue(json, ExternalId.class)).isEqualTo(id);
    }

    @Test
    void moneyRoundTripsWithAnExactTwoDecimalStringAndStableCurrencyCode() throws Exception {
        var money = Money.cny(new BigDecimal("99.80"));

        var json = mapper.writeValueAsString(money);

        assertThat(json).contains("\"amount\":\"99.80\"");
        assertThat(json).contains("\"currency\":\"CNY\"");
        assertThat(json).doesNotContain("99.8,");
        var restored = mapper.readValue(json, Money.class);
        assertThat(restored).isEqualTo(money);
        assertThat(restored.amount().scale()).isEqualTo(2);
    }

    @Test
    void currencyEnumUsesItsStableCodeInBothDirections() throws Exception {
        var json = mapper.writeValueAsString(CurrencyCode.CNY);

        assertThat(json).isEqualTo("\"CNY\"");
        assertThat(mapper.readValue(json, CurrencyCode.class)).isEqualTo(CurrencyCode.CNY);
    }

    @Test
    void offsetDateTimeRoundTripsAsIso8601WithoutLosingTheOriginalOffset() throws Exception {
        var occurredAt = OffsetDateTime.of(
                2026, 7, 11, 15, 0, 0, 0, ZoneOffset.ofHours(8));
        var source = new TimeEnvelope(occurredAt);

        var json = mapper.writeValueAsString(source);

        assertThat(json).contains("2026-07-11T15:00:00+08:00");
        var restored = mapper.readValue(json, TimeEnvelope.class);
        assertThat(restored.occurredAt()).isEqualTo(occurredAt);
        assertThat(restored.occurredAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));
    }

    @Test
    void exposesLegacyMapperForAdaptersAwaitingJacksonThreeMigration() throws Exception {
        var legacy = new YghJacksonConfiguration().legacyObjectMapper();

        assertThat(legacy.writeValueAsString(java.util.Map.of("status", "ok")))
                .isEqualTo("{\"status\":\"ok\"}");
    }

    private record TimeEnvelope(OffsetDateTime occurredAt) {
    }
}
