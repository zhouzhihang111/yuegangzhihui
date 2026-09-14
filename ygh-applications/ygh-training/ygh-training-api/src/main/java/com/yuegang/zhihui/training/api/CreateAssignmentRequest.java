package com.yuegang.zhihui.training.api;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public record CreateAssignmentRequest(@NotBlank String userId, String pathId, @NotBlank String courseId, OffsetDateTime dueAt) {}
