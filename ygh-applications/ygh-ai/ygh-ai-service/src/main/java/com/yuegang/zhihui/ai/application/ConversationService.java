package com.yuegang.zhihui.ai.application;

import com.yuegang.zhihui.ai.api.CitationView;
import com.yuegang.zhihui.ai.api.ConversationView;
import com.yuegang.zhihui.ai.api.FeedbackRequest;
import com.yuegang.zhihui.ai.api.MessageView;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ConversationService {
    private final JdbcTemplate jdbc;

    public ConversationService(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
    }

    public List<ConversationView> list(long user, int limit) {
        return jdbc.query("SELECT id,title,status,updated_at FROM ai_conversation "
                        + "WHERE user_id=? ORDER BY updated_at DESC LIMIT ?",
                (result, row) -> new ConversationView(Long.toString(result.getLong(1)), result.getString(2),
                        result.getString(3), time(result.getTimestamp(4))), user, Math.max(1, Math.min(limit, 100)));
    }

    public List<MessageView> messages(long user, String conversation) {
        long conversationId = id(conversation);
        Integer owned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE id=? AND user_id=?",
                Integer.class, conversationId, user);
        if (owned == null || owned != 1) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return jdbc.query("SELECT id,role,content,refused,created_at FROM ai_message "
                        + "WHERE conversation_id=? ORDER BY created_at,id",
                (result, row) -> {
                    long messageId = result.getLong(1);
                    return new MessageView(Long.toString(messageId), result.getString(2), result.getString(3),
                            result.getBoolean(4), time(result.getTimestamp(5)), citations(messageId));
                }, conversationId);
    }

    public void feedback(long user, FeedbackRequest request) {
        long message = id(request.messageId());
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM ai_message m "
                        + "JOIN ai_conversation c ON c.id=m.conversation_id "
                        + "WHERE m.id=? AND c.user_id=? AND m.role='ASSISTANT'",
                Integer.class, message, user);
        if (owned == null || owned != 1) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        jdbc.update("INSERT INTO ai_feedback(id,message_id,user_id,helpful,comment) VALUES(?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE helpful=VALUES(helpful),comment=VALUES(comment)",
                next(), message, user, request.helpful(), request.comment());
    }

    private List<CitationView> citations(long messageId) {
        return jdbc.query("SELECT source_type,source_id,document_id,title,excerpt,url,document_version,source_updated_at "
                        + "FROM ai_citation WHERE message_id=? ORDER BY sequence_no",
                (result, row) -> new CitationView(result.getString(1), result.getString(2), result.getString(3),
                        result.getString(4), result.getString(5), result.getString(6), result.getLong(7),
                        time(result.getTimestamp(8))), messageId);
    }

    private static OffsetDateTime time(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }

    private static long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static long next() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
}
