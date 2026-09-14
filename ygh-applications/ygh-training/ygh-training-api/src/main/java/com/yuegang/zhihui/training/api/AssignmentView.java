package com.yuegang.zhihui.training.api;

import java.time.OffsetDateTime;

public record AssignmentView(String assignmentId, String userId, String courseId, String status, OffsetDateTime dueAt) {}
