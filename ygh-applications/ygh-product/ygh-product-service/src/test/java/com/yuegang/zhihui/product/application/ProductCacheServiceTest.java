package com.yuegang.zhihui.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.product.api.ProductStatus;
import com.yuegang.zhihui.product.api.ProductView;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class ProductCacheServiceTest {
    @Test
    void cachesDetailListsMissesAndToleratesRedisFailure() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var mapper = new ObjectMapper();
        var service = new ProductCacheService(redis, mapper);
        var product = product();

        when(values.get("ygh:product:detail:1")).thenReturn(mapper.writeValueAsString(product));
        var loads = new AtomicInteger();
        assertThat(service.detail("1", () -> { loads.incrementAndGet(); return product; })).isEqualTo(product);
        assertThat(loads).hasValue(0);

        when(values.get("ygh:product:detail:2")).thenReturn(null);
        assertThat(service.detail("2", () -> product)).isEqualTo(product);
        verify(values).set("ygh:product:detail:2", mapper.writeValueAsString(product), Duration.ofMinutes(10));

        when(values.get("ygh:product:detail:3")).thenReturn("__MISS__");
        assertThatThrownBy(() -> service.detail("3", ProductCacheServiceTest::product)).isInstanceOf(BusinessException.class);
        when(values.get("ygh:product:detail:4")).thenReturn(null);
        assertThatThrownBy(() -> service.detail("4", () -> { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); }))
                .isInstanceOf(BusinessException.class);
        verify(values).set("ygh:product:detail:4", "__MISS__", Duration.ofSeconds(30));

        when(values.get("ygh:product:catalog-version")).thenReturn("5");
        when(values.get(anyString())).thenAnswer(invocation -> invocation.getArgument(0).toString().startsWith("ygh:product:list:")
                ? mapper.writeValueAsString(List.of(product)) : null);
        assertThat(service.list("cat", " snack ", 10, List::of)).containsExactly(product);

        when(redis.delete(anyString())).thenThrow(new IllegalStateException("redis unavailable"));
        service.invalidate("1");
        verify(values, never()).increment("ygh:product:catalog-version");
    }

    private static ProductView product() {
        return new ProductView("10", "1", "20", null, "商品", "SKU-1", BigDecimal.TEN, "CNY",
                ProductStatus.PUBLISHED, List.of(), "TRACE", 1);
    }
}
