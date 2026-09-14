package com.yuegang.zhihui.training.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.training.api.AssignmentView;
import com.yuegang.zhihui.training.api.CreateAssignmentRequest;
import com.yuegang.zhihui.training.api.TrainingStatisticsView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class TrainingAssignmentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TrainingAssignmentService(DataSource dataSource, ObjectMapper json) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.json = json;
    }

    @Transactional
    public AssignmentView assign(long operator, CreateAssignmentRequest command) {
        Long path = command.pathId() == null || command.pathId().isBlank()
                ? null
                : positive(command.pathId());
        long user = positive(command.userId());
        long course = positive(command.courseId());
        Optional<AssignmentView> existing = findExisting(user, course);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        long id = System.currentTimeMillis() * 1000
                + Math.floorMod((command.userId() + command.courseId()).hashCode(), 1000);
        int inserted = jdbc.update("""
                INSERT INTO training_assignment(id,user_id,path_id,course_id,assigned_by,due_at)
                SELECT ?,?,?,?,?,?
                  WHERE EXISTS(SELECT 1 FROM training_course WHERE id=? AND status='PUBLISHED')
                """, id, user, path, course, operator,
                command.dueAt() == null ? null : Timestamp.from(command.dueAt().toInstant()), course);
        if (inserted != 1) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);

        String title = jdbc.queryForObject("SELECT title FROM training_course WHERE id=?", String.class, course);
        String event = "training-assigned-" + id;
        jdbc.update("""
                INSERT INTO training_outbox(id,aggregate_id,event_type,payload_json) VALUES(?,?,?,?)
                """, event, Long.toString(id), "TRAINING_ASSIGNED",
                write(Map.of("assignmentId", Long.toString(id), "userId", Long.toString(user),
                        "courseId", Long.toString(course), "courseTitle", title == null ? "" : title)));
        return find(id);
    }

    public java.util.List<AssignmentView> mine(long user) {
        return jdbc.query("""
                SELECT id,user_id,course_id,status,due_at
                  FROM (
                    SELECT a.*, ROW_NUMBER() OVER(
                        PARTITION BY user_id,course_id
                        ORDER BY CASE status WHEN 'ASSIGNED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 WHEN 'COMPLETED' THEN 3 ELSE 4 END,
                                 assigned_at DESC,id DESC
                    ) rn
                      FROM training_assignment a WHERE user_id=?
                  ) ranked
                 WHERE rn=1 ORDER BY assigned_at DESC
                """, (row, index) -> map(row), user);
    }

    public TrainingStatisticsView statistics() {
        return jdbc.query("""
                SELECT COUNT(*) assigned,SUM(status='IN_PROGRESS') progressing,
                       SUM(status='COMPLETED') completed,
                       SUM(status<>'COMPLETED' AND due_at<NOW(6)) overdue
                  FROM (
                    SELECT ranked.* FROM (
                        SELECT a.*, ROW_NUMBER() OVER(
                            PARTITION BY user_id,course_id
                            ORDER BY CASE status WHEN 'ASSIGNED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 WHEN 'COMPLETED' THEN 3 ELSE 4 END,
                                     assigned_at DESC,id DESC
                        ) rn
                          FROM training_assignment a
                    ) ranked WHERE ranked.rn=1
                  ) a
                """, row -> {
            if (!row.next()) return new TrainingStatisticsView(0, 0, 0, 0, BigDecimal.ZERO);
            long assigned = row.getLong("assigned"), completed = row.getLong("completed");
            BigDecimal rate = assigned == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(completed * 100)
                    .divide(BigDecimal.valueOf(assigned), 2, RoundingMode.HALF_UP);
            return new TrainingStatisticsView(assigned, row.getLong("progressing"),
                    completed, row.getLong("overdue"), rate);
        });
    }

    private Optional<AssignmentView> findExisting(long user, long course) {
        return jdbc.query("""
                SELECT id,user_id,course_id,status,due_at
                  FROM training_assignment
                 WHERE user_id=? AND course_id=?
                 ORDER BY CASE status WHEN 'ASSIGNED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 WHEN 'COMPLETED' THEN 3 ELSE 4 END,
                          assigned_at DESC
                 LIMIT 1
                """, row -> row.next() ? Optional.of(map(row)) : Optional.empty(), user, course);
    }

    private AssignmentView find(long id) {
        return jdbc.query("""
                SELECT id,user_id,course_id,status,due_at FROM training_assignment WHERE id=?
                """, row -> {
            if (!row.next()) throw new BusinessException(ErrorCode.BUSINESS_CONFLICT);
            return map(row);
        }, id);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static AssignmentView map(ResultSet row) throws java.sql.SQLException {
        Timestamp due = row.getTimestamp("due_at");
        return new AssignmentView(Long.toString(row.getLong("id")),
                Long.toString(row.getLong("user_id")), Long.toString(row.getLong("course_id")),
                row.getString("status"), due == null ? null : due.toInstant().atOffset(ZoneOffset.UTC));
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
}
