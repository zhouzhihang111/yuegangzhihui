package com.yuegang.zhihui.training.api;

import java.time.OffsetDateTime;

public record DocumentProgressView(String assignmentId, String documentId, String chapterId, String fileName,
                                   String status, OffsetDateTime openedAt, OffsetDateTime completedAt,
                                   long version) {
}
