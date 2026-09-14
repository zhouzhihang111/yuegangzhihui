package com.yuegang.zhihui.search.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeleteDocumentCommand(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:._-]{0,63}") String documentId,
        @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}") String indexName) {
    public DeleteDocumentCommand(String documentId) { this(documentId, null); }
}
