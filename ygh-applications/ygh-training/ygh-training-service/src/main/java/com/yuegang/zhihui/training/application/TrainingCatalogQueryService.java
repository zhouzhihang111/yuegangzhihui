package com.yuegang.zhihui.training.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.training.api.GateView;
import com.yuegang.zhihui.training.api.TrainingDocumentView;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TrainingCatalogQueryService {
    private final JdbcTemplate jdbc;

    public TrainingCatalogQueryService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<GateView> gates(String courseId) {
        return jdbc.query("""
                SELECT g.id,g.chapter_id,g.title,g.pass_score,g.maximum_attempts
                FROM training_gate g JOIN training_chapter c ON c.id=g.chapter_id
                WHERE c.course_id=? ORDER BY c.sequence_no,g.id
                """, (row, index) -> new GateView(
                Long.toString(row.getLong("id")), Long.toString(row.getLong("chapter_id")),
                row.getString("title"), row.getInt("pass_score"),
                nullableInteger(row.getObject("maximum_attempts"))), positive(courseId));
    }

    public List<TrainingDocumentView> documents(String chapterId) {
        return jdbc.query("""
                SELECT id,chapter_id,file_name,media_type,size_bytes,status
                FROM training_document WHERE chapter_id=? AND status='ACTIVE' ORDER BY id
                """, (row, index) -> new TrainingDocumentView(
                Long.toString(row.getLong("id")), Long.toString(row.getLong("chapter_id")),
                row.getString("file_name"), row.getString("media_type"),
                row.getLong("size_bytes"), row.getString("status")), positive(chapterId));
    }

    private static long positive(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException failure) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }
}
