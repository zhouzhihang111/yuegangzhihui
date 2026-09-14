package com.yuegang.zhihui.search.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record IndexChunkCommand(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:._-]{0,63}") String documentId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:._-]{0,63}") String chunkId,
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Size(max = 50000) String content,
        @NotBlank @Size(max = 100) String category,
        @NotBlank @Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL") String visibility,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9][a-z0-9._-]{1,63}") String indexVersion,
        @PositiveOrZero long documentVersion,
        OffsetDateTime sourceUpdatedAt,
        @AssertTrue boolean published) {
    public IndexChunkCommand(String documentId, String chunkId, String title, String content, String category,
                             String visibility, String indexVersion, boolean published) {
        this(documentId, chunkId, title, content, category, visibility, indexVersion, 0, null, published);
    }
}
