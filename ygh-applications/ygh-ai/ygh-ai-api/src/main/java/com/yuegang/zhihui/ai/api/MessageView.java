package com.yuegang.zhihui.ai.api;

import java.time.OffsetDateTime;
import java.util.List;

public record MessageView(String id, String role, String content, boolean refused,
                          OffsetDateTime createdAt, List<CitationView> citations) {
    public MessageView {
        citations = List.copyOf(citations);
    }
}
