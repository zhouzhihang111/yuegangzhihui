package com.yuegang.zhihui.knowledge.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RebuildKnowledgeIndexRequest(
        @NotBlank
        @Pattern(regexp = "[a-z0-9][a-z0-9._-]{1,63}")
        String version) { }
