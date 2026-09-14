package com.yuegang.zhihui.order.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yuegang.zhihui.common.mq.JdbcOutboxDispatcher;
import org.junit.jupiter.api.Test;

class OrderJobsTest {
    @Test
    void scheduledJobsDelegate() {
        var facade = mock(OrderInventoryFacade.class);
        new OrderExpiryJob(facade).closeExpired();
        verify(facade).closeExpired();
        var dispatcher = mock(JdbcOutboxDispatcher.class);
        var outbox = new OrderOutboxConfiguration.OrderOutboxJob(dispatcher, facade);
        outbox.dispatch();
        outbox.closeExpired();
        verify(dispatcher).dispatch();
        verify(facade, org.mockito.Mockito.times(2)).closeExpired();
    }
}
