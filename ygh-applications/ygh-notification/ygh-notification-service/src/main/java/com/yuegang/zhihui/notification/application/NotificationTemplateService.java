package com.yuegang.zhihui.notification.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.notification.api.NotificationTemplateView;
import com.yuegang.zhihui.notification.api.SaveNotificationTemplateRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public final class NotificationTemplateService {
    private final JdbcTemplate jdbc;

    public NotificationTemplateService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<NotificationTemplateView> list() {
        return jdbc.query("SELECT code,title_template,content_template,channel,enabled,version FROM notification_template ORDER BY code", (row, index) -> map(row));
    }

    public NotificationTemplateView save(String code, SaveNotificationTemplateRequest request) {
        if (!code.equals(request.code())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        int changed = jdbc.update(
                "UPDATE notification_template SET title_template=?,content_template=?,channel=?,enabled=?,version=version+1 WHERE code=? AND version=?",
                request.titleTemplate(), request.contentTemplate(), request.channel(), request.enabled(), code, request.version());
        if (changed == 0 && request.version() == 0) {
            try {
                jdbc.update("INSERT INTO notification_template(id,code,title_template,content_template,channel,enabled) VALUES(?,?,?,?,?,?)",
                        nextId(), code, request.titleTemplate(), request.contentTemplate(), request.channel(), request.enabled());
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            }
        } else if (changed == 0) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
        return find(code);
    }

    private NotificationTemplateView find(String code) {
        return jdbc.query("SELECT code,title_template,content_template,channel,enabled,version FROM notification_template WHERE code=?", result -> {
            if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            return map(result);
        }, code);
    }

    private static NotificationTemplateView map(ResultSet row) throws SQLException {
        return new NotificationTemplateView(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getBoolean(5), row.getLong(6));
    }

    private static long nextId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
