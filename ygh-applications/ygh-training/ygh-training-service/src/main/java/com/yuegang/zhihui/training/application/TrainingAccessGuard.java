package com.yuegang.zhihui.training.application;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.training.security.TrainingUserContext;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TrainingAccessGuard {
    private final JdbcTemplate jdbc;

    public TrainingAccessGuard(DataSource dataSource) { jdbc = new JdbcTemplate(dataSource); }

    public Set<String> assignedCourseIds(TrainingUserContext user) {
        if (user.courseManager()) return Set.of();
        return jdbc.queryForList("SELECT DISTINCT CAST(c.id AS CHAR) FROM training_course c "
                        + "JOIN training_assignment a ON a.course_id=c.id "
                        + "WHERE a.user_id=? AND c.status='PUBLISHED'", String.class, user.userId())
                .stream().collect(Collectors.toUnmodifiableSet());
    }

    public void requireCourse(TrainingUserContext user, String courseId) {
        require(user, "SELECT COUNT(*) FROM training_assignment a JOIN training_course c ON c.id=a.course_id "
                + "WHERE a.user_id=? AND c.id=? AND c.status='PUBLISHED'", courseId);
    }

    public void requireChapter(TrainingUserContext user, String chapterId) {
        require(user, "SELECT COUNT(*) FROM training_assignment a JOIN training_course c ON c.id=a.course_id "
                + "JOIN training_chapter ch ON ch.course_id=c.id WHERE a.user_id=? AND ch.id=? "
                + "AND c.status='PUBLISHED'", chapterId);
    }

    public void requireGate(TrainingUserContext user, String gateId) {
        require(user, "SELECT COUNT(*) FROM training_assignment a JOIN training_course c ON c.id=a.course_id "
                + "JOIN training_chapter ch ON ch.course_id=c.id JOIN training_gate g ON g.chapter_id=ch.id "
                + "WHERE a.user_id=? AND g.id=? AND c.status='PUBLISHED'", gateId);
    }

    private void require(TrainingUserContext user, String sql, String resourceId) {
        if (user.courseManager()) return;
        Integer count = jdbc.queryForObject(sql, Integer.class, user.userId(), positive(resourceId));
        if (count == null || count < 1) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
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
