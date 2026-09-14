package com.yuegang.zhihui.product.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yuegang.zhihui.common.mq.JdbcOutboxDispatcher;
import org.junit.jupiter.api.Test;

class ProductOutboxConfigurationTest {
    @Test
    void scheduledJobDelegatesToDispatcher() {
        var dispatcher = mock(JdbcOutboxDispatcher.class);
        new ProductOutboxConfiguration.ProductOutboxJob(dispatcher).dispatch();
        verify(dispatcher).dispatch();
    }
}
