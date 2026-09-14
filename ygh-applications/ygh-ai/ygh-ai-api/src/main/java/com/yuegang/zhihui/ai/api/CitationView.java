package com.yuegang.zhihui.ai.api;

import java.time.OffsetDateTime;

public record CitationView(
        String sourceType,
        String sourceId,
        String documentId,
        String title,
        String excerpt,
        String url,
        long documentVersion,
        OffsetDateTime sourceUpdatedAt) {

    public CitationView(String sourceType, String sourceId, String title, String excerpt, String url) {
        this(sourceType, sourceId, null, title, excerpt, url, 0, null);
    }
}
