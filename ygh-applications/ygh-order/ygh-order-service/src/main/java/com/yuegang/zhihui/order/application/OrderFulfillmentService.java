package com.yuegang.zhihui.order.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.order.api.OrderStatus;
import com.yuegang.zhihui.order.api.OrderView;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class OrderFulfillmentService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public OrderFulfillmentService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public OrderView start(String orderId, long version) {
        return transition(orderId, version, "PAID", "PROCESSING", "ORDER_PROCESSING");
    }

    public OrderView complete(String orderId, long version) {
        return transition(orderId, version, "PROCESSING", "COMPLETED", "ORDER_COMPLETED");
    }

    private OrderView transition(String orderId, long version, String from, String to, String eventType) {
        return transaction.execute(status -> {
            long id = positive(orderId);
            int changed = jdbc.update(
                    "UPDATE order_main SET status=?,version=version+1 WHERE id=? AND version=? AND status=?",
                    to, id, version, from);
            if (changed != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            jdbc.update(
                    "INSERT INTO order_state_history(id,order_id,from_status,to_status,reason) VALUES(?,?,?,?,?)",
                    nextId(), id, from, to, "ADMIN_SIMULATED_FULFILLMENT");
            String eventId = UUID.randomUUID().toString();
            jdbc.update(
                    "INSERT INTO order_outbox(id,aggregate_id,event_type,payload_json) VALUES(?,?,?,JSON_OBJECT('eventId',?,'orderId',?))",
                    eventId, orderId, eventType, eventId, orderId);
            return find(id);
        });
    }

    private OrderView find(long id) {
        return jdbc.query(
                "SELECT id,order_no,user_id,status,total_amount,currency,version,created_at FROM order_main WHERE id=?",
                result -> {
                    if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
                    return new OrderView(
                            Long.toString(result.getLong(1)), result.getString(2), Long.toString(result.getLong(3)),
                            OrderStatus.valueOf(result.getString(4)), result.getBigDecimal(5), result.getString(6),
                            result.getLong(7), result.getTimestamp(8).toLocalDateTime().atOffset(ZoneOffset.UTC));
                }, id);
    }

    private static long positive(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static long nextId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
