package com.yuegang.zhihui.training.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record EmployeeLearningProgressView(String assignmentId, String userId, String courseId, String courseTitle,
                                           String status, BigDecimal progressPercent, int completedDocuments,
                                           int totalDocuments, Integer bestScore, OffsetDateTime dueAt,
                                           OffsetDateTime completedAt) {
}
