package com.yuegang.zhihui.knowledge.api;

import java.time.OffsetDateTime;

public record KnowledgeIndexJobView(
        String id,
        String documentId,
        String indexVersion,
        String jobType,
        String status,
        int progress,
        int retryCount,
        String failureReason,
        OffsetDateTime updatedAt) {}
