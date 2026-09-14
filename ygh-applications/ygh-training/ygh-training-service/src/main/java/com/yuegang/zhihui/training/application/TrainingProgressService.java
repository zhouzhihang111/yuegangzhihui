package com.yuegang.zhihui.training.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.training.api.LearningHeartbeat;
import com.yuegang.zhihui.training.api.ProgressView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class TrainingProgressService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public TrainingProgressService(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public ProgressView heartbeat(long user, LearningHeartbeat heartbeat) {
        return tx.execute(status -> {
            long assignment = id(heartbeat.assignmentId()), chapter = id(heartbeat.chapterId());
            Integer allowed = jdbc.queryForObject("SELECT COUNT(*) FROM training_assignment a " +
                    "JOIN training_chapter current ON current.id=? AND current.course_id=a.course_id " +
                    "WHERE a.id=? AND a.user_id=? AND a.status IN ('ASSIGNED','IN_PROGRESS') " +
                    "AND NOT EXISTS(SELECT 1 FROM training_chapter previous WHERE previous.course_id=a.course_id " +
                    "AND previous.sequence_no<current.sequence_no AND (NOT EXISTS(SELECT 1 FROM training_chapter_progress p " +
                    "WHERE p.assignment_id=a.id AND p.chapter_id=previous.id AND p.completed=TRUE) OR EXISTS(SELECT 1 FROM training_gate g " +
                    "WHERE g.chapter_id=previous.id AND NOT EXISTS(SELECT 1 FROM training_quiz_attempt q WHERE q.assignment_id=a.id " +
                    "AND q.gate_id=g.id AND q.passed=TRUE))))", Integer.class, chapter, assignment, user);
            if (allowed != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            jdbc.update("INSERT INTO training_chapter_progress(assignment_id,chapter_id,last_nonce) VALUES(?,?,NULL) " +
                    "ON DUPLICATE KEY UPDATE assignment_id=VALUES(assignment_id)", assignment, chapter);
            jdbc.update("UPDATE training_chapter_progress p JOIN training_chapter c ON c.id=p.chapter_id " +
                    "SET p.active_seconds=p.active_seconds+?,p.last_nonce=?,p.completed=(p.active_seconds+?>=c.minimum_active_seconds)," +
                    "p.completed_at=CASE WHEN p.active_seconds+?>=c.minimum_active_seconds THEN COALESCE(p.completed_at,NOW(6)) ELSE NULL END," +
                    "p.version=p.version+1 WHERE p.assignment_id=? AND p.chapter_id=? AND (p.last_nonce IS NULL OR p.last_nonce<>?)",
                    heartbeat.activeSeconds(), heartbeat.nonce(), heartbeat.activeSeconds(), heartbeat.activeSeconds(),
                    assignment, chapter, heartbeat.nonce());
            jdbc.update("""
                    UPDATE training_chapter_progress p
                       SET p.completed=FALSE,p.completed_at=NULL,p.version=p.version+1
                     WHERE p.assignment_id=? AND p.chapter_id=?
                       AND EXISTS(SELECT 1 FROM training_document d
                           LEFT JOIN training_document_progress dp ON dp.document_id=d.id AND dp.assignment_id=p.assignment_id
                           WHERE d.chapter_id=p.chapter_id AND d.status='ACTIVE'
                             AND COALESCE(dp.status,'NOT_STARTED')<>'COMPLETED')
                    """, assignment, chapter);
            jdbc.update("UPDATE training_assignment SET status='IN_PROGRESS',version=version+1 WHERE id=? AND status='ASSIGNED'", assignment);
            completeIfEligible(assignment);
            return view(user, assignment);
        });
    }

    public ProgressView get(long user, String assignment) { return view(user, id(assignment)); }

    private void completeIfEligible(long assignment) {
        jdbc.update("UPDATE training_assignment a SET status='COMPLETED',completed_at=NOW(6),version=version+1 " +
                "WHERE a.id=? AND a.status<>'COMPLETED' AND NOT EXISTS(SELECT 1 FROM training_chapter c " +
                "LEFT JOIN training_chapter_progress p ON p.chapter_id=c.id AND p.assignment_id=a.id " +
                "WHERE c.course_id=a.course_id AND COALESCE(p.completed,FALSE)=FALSE) AND NOT EXISTS(SELECT 1 FROM training_gate g " +
                "JOIN training_chapter c ON c.id=g.chapter_id WHERE c.course_id=a.course_id AND NOT EXISTS(SELECT 1 FROM training_quiz_attempt q " +
                "WHERE q.assignment_id=a.id AND q.gate_id=g.id AND q.passed=TRUE)) " +
                "AND NOT EXISTS(SELECT 1 FROM training_document d JOIN training_chapter c ON c.id=d.chapter_id " +
                "LEFT JOIN training_document_progress dp ON dp.document_id=d.id AND dp.assignment_id=a.id " +
                "WHERE c.course_id=a.course_id AND d.status='ACTIVE' AND COALESCE(dp.status,'NOT_STARTED')<>'COMPLETED')", assignment);
    }

    private ProgressView view(long user, long assignment) {
        return jdbc.query("SELECT a.id,a.course_id,a.user_id,a.status,a.version," +
                "(SELECT COUNT(*) FROM training_chapter WHERE course_id=a.course_id) total," +
                "(SELECT COUNT(*) FROM training_chapter_progress WHERE assignment_id=a.id AND completed=TRUE) done," +
                "(SELECT COUNT(*) FROM training_document d JOIN training_chapter c ON c.id=d.chapter_id WHERE c.course_id=a.course_id AND d.status='ACTIVE') total_docs," +
                "(SELECT COUNT(*) FROM training_document_progress dp JOIN training_document d ON d.id=dp.document_id JOIN training_chapter c ON c.id=d.chapter_id WHERE dp.assignment_id=a.id AND c.course_id=a.course_id AND dp.status='COMPLETED') done_docs," +
                "(SELECT MAX(score) FROM training_quiz_attempt WHERE assignment_id=a.id) best," +
                "(SELECT MIN(c.id) FROM training_chapter c LEFT JOIN training_chapter_progress p ON p.chapter_id=c.id " +
                "AND p.assignment_id=a.id WHERE c.course_id=a.course_id AND COALESCE(p.completed,FALSE)=FALSE) current " +
                "FROM training_assignment a WHERE a.id=? AND a.user_id=?", result -> {
            if (!result.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            int total = result.getInt("total") + result.getInt("total_docs");
            int done = result.getInt("done") + result.getInt("done_docs");
            BigDecimal percent = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(done).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            Object current = result.getObject("current"), best = result.getObject("best");
            return new ProgressView(Long.toString(result.getLong("id")), Long.toString(result.getLong("course_id")),
                    Long.toString(result.getLong("user_id")), percent, result.getString("status"),
                    current == null ? null : current.toString(), best instanceof Number number ? number.intValue() : null,
                    result.getLong("version"));
        }, assignment, user);
    }

    private static long id(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException failure) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
