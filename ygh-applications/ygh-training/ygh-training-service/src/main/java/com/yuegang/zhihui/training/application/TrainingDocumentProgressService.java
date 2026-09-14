package com.yuegang.zhihui.training.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.training.api.DocumentProgressView;
import com.yuegang.zhihui.training.api.EmployeeLearningProgressView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class TrainingDocumentProgressService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public TrainingDocumentProgressService(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public void recordOpened(long userId, String assignmentId, String documentId) {
        long assignment = positive(assignmentId), document = positive(documentId);
        requireOwnedDocument(userId, assignment, document);
        jdbc.update("""
                INSERT INTO training_document_progress(assignment_id,document_id,status,opened_at)
                VALUES(?,?,'IN_PROGRESS',NOW(6))
                ON DUPLICATE KEY UPDATE
                  status=IF(status='NOT_STARTED','IN_PROGRESS',status),
                  opened_at=COALESCE(opened_at,NOW(6)),version=version+1
                """, assignment, document);
        jdbc.update("UPDATE training_assignment SET status='IN_PROGRESS',version=version+1 " +
                "WHERE id=? AND status='ASSIGNED'", assignment);
    }

    public DocumentProgressView complete(long userId, String assignmentId, String documentId) {
        return tx.execute(ignored -> {
            long assignment = positive(assignmentId), document = positive(documentId);
            requireOwnedDocument(userId, assignment, document);
            int updated = jdbc.update("""
                    UPDATE training_document_progress
                       SET status='COMPLETED',completed_at=COALESCE(completed_at,NOW(6)),version=version+1
                     WHERE assignment_id=? AND document_id=? AND opened_at IS NOT NULL
                    """, assignment, document);
            if (updated != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            long chapter = jdbc.queryForObject("SELECT chapter_id FROM training_document WHERE id=?", Long.class, document);
            refreshChapterAndAssignment(assignment, chapter);
            return one(userId, assignment, document);
        });
    }

    public List<DocumentProgressView> documents(long userId, String assignmentId, String chapterId) {
        long assignment = positive(assignmentId), chapter = positive(chapterId);
        Integer owned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM training_assignment a
                JOIN training_chapter c ON c.course_id=a.course_id
                WHERE a.id=? AND a.user_id=? AND c.id=?
                """, Integer.class, assignment, userId, chapter);
        if (owned == null || owned != 1) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return jdbc.query("""
                SELECT d.id,d.chapter_id,d.file_name,COALESCE(p.status,'NOT_STARTED'),
                       p.opened_at,p.completed_at,COALESCE(p.version,0)
                  FROM training_document d
                  LEFT JOIN training_document_progress p ON p.document_id=d.id AND p.assignment_id=?
                 WHERE d.chapter_id=? AND d.status='ACTIVE' ORDER BY d.id
                """, (row, index) -> map(assignment, row.getLong(1), row.getLong(2), row.getString(3),
                row.getString(4), row.getTimestamp(5), row.getTimestamp(6), row.getLong(7)), assignment, chapter);
    }

    public List<EmployeeLearningProgressView> employeeProgress() {
        return jdbc.query("""
                SELECT a.id,a.user_id,a.course_id,c.title,a.status,a.due_at,a.completed_at,
                       (SELECT COUNT(*) FROM training_document d JOIN training_chapter ch ON ch.id=d.chapter_id
                         WHERE ch.course_id=a.course_id AND d.status='ACTIVE') total_docs,
                       (SELECT COUNT(*) FROM training_document_progress p JOIN training_document d ON d.id=p.document_id
                         JOIN training_chapter ch ON ch.id=d.chapter_id
                         WHERE p.assignment_id=a.id AND ch.course_id=a.course_id AND p.status='COMPLETED') done_docs,
                       (SELECT COUNT(*) FROM training_chapter WHERE course_id=a.course_id) total_chapters,
                       (SELECT COUNT(*) FROM training_chapter_progress WHERE assignment_id=a.id AND completed=TRUE) done_chapters,
                       (SELECT MAX(score) FROM training_quiz_attempt WHERE assignment_id=a.id) best
                  FROM (
                    SELECT ranked.* FROM (
                        SELECT a.*, ROW_NUMBER() OVER(
                            PARTITION BY user_id,course_id
                            ORDER BY CASE status WHEN 'ASSIGNED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 WHEN 'COMPLETED' THEN 3 ELSE 4 END,
                                     assigned_at DESC,id DESC
                        ) rn
                          FROM training_assignment a
                    ) ranked WHERE ranked.rn=1
                  ) a JOIN training_course c ON c.id=a.course_id
                 ORDER BY a.assigned_at DESC
                """, (row, index) -> {
            int totalDocs = row.getInt("total_docs"), doneDocs = row.getInt("done_docs");
            int total = totalDocs > 0 ? totalDocs : row.getInt("total_chapters");
            int done = totalDocs > 0 ? doneDocs : row.getInt("done_chapters");
            BigDecimal percent = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(done * 100L)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            Object best = row.getObject("best");
            return new EmployeeLearningProgressView(Long.toString(row.getLong("id")),
                    Long.toString(row.getLong("user_id")), Long.toString(row.getLong("course_id")),
                    row.getString("title"), row.getString("status"), percent, doneDocs, totalDocs,
                    best instanceof Number number ? number.intValue() : null,
                    offset(row.getTimestamp("due_at")), offset(row.getTimestamp("completed_at")));
        });
    }

    private DocumentProgressView one(long userId, long assignment, long document) {
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM training_assignment WHERE id=? AND user_id=?",
                Integer.class, assignment, userId);
        if (owned == null || owned != 1) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return jdbc.query("""
                SELECT d.chapter_id,d.file_name,p.status,p.opened_at,p.completed_at,p.version
                  FROM training_document_progress p JOIN training_document d ON d.id=p.document_id
                 WHERE p.assignment_id=? AND p.document_id=?
                """, row -> {
            if (!row.next()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            return map(assignment, document, row.getLong(1), row.getString(2), row.getString(3),
                    row.getTimestamp(4), row.getTimestamp(5), row.getLong(6));
        }, assignment, document);
    }

    private void requireOwnedDocument(long user, long assignment, long document) {
        Integer owned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM training_assignment a
                JOIN training_chapter c ON c.course_id=a.course_id
                JOIN training_document d ON d.chapter_id=c.id AND d.status='ACTIVE'
                WHERE a.id=? AND a.user_id=? AND d.id=? AND a.status IN('ASSIGNED','IN_PROGRESS','COMPLETED')
                """, Integer.class, assignment, user, document);
        if (owned == null || owned != 1) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private void refreshChapterAndAssignment(long assignment, long chapter) {
        jdbc.update("""
                UPDATE training_chapter_progress p JOIN training_chapter c ON c.id=p.chapter_id
                   SET p.completed=(p.active_seconds>=c.minimum_active_seconds),
                       p.completed_at=CASE WHEN p.active_seconds>=c.minimum_active_seconds THEN COALESCE(p.completed_at,NOW(6)) ELSE NULL END,
                       p.version=p.version+1
                 WHERE p.assignment_id=? AND p.chapter_id=?
                   AND NOT EXISTS(SELECT 1 FROM training_document d
                       LEFT JOIN training_document_progress dp ON dp.document_id=d.id AND dp.assignment_id=p.assignment_id
                       WHERE d.chapter_id=p.chapter_id AND d.status='ACTIVE' AND COALESCE(dp.status,'NOT_STARTED')<>'COMPLETED')
                """, assignment, chapter);
        jdbc.update("""
                UPDATE training_assignment a SET status='COMPLETED',completed_at=NOW(6),version=version+1
                 WHERE a.id=? AND a.status<>'COMPLETED'
                   AND NOT EXISTS(SELECT 1 FROM training_chapter c LEFT JOIN training_chapter_progress p
                       ON p.chapter_id=c.id AND p.assignment_id=a.id
                       WHERE c.course_id=a.course_id AND COALESCE(p.completed,FALSE)=FALSE)
                   AND NOT EXISTS(SELECT 1 FROM training_document d JOIN training_chapter c ON c.id=d.chapter_id
                       LEFT JOIN training_document_progress dp ON dp.document_id=d.id AND dp.assignment_id=a.id
                       WHERE c.course_id=a.course_id AND d.status='ACTIVE' AND COALESCE(dp.status,'NOT_STARTED')<>'COMPLETED')
                   AND NOT EXISTS(SELECT 1 FROM training_gate g JOIN training_chapter c ON c.id=g.chapter_id
                       WHERE c.course_id=a.course_id AND NOT EXISTS(SELECT 1 FROM training_quiz_attempt q
                           WHERE q.assignment_id=a.id AND q.gate_id=g.id AND q.passed=TRUE))
                """, assignment);
    }

    private static DocumentProgressView map(long assignment, long document, long chapter, String fileName,
                                            String status, Timestamp opened, Timestamp completed, long version) {
        return new DocumentProgressView(Long.toString(assignment), Long.toString(document), Long.toString(chapter),
                fileName, status, offset(opened), offset(completed), version);
    }
    private static OffsetDateTime offset(Timestamp value) {
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }
    private static long positive(String value) {
        try { long id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; }
        catch (RuntimeException invalid) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
    }
}
