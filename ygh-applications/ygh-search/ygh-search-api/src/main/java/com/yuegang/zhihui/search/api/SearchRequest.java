package com.yuegang.zhihui.search.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

public record SearchRequest(
        @NotBlank @Size(max = 500) String query,
        @Min(1) @Max(50) int limit,
        @Size(max = 100) String category,
        @NotEmpty @Size(max = 3) Set<@Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL") String> visibilities
) {
    public SearchRequest(String query, int limit, String category) {
        this(query, limit, category, Set.of("PUBLIC"));
    }

    public SearchRequest {
        query = query == null ? null : query.strip();
        visibilities = visibilities == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(visibilities));
        category = category == null || category.isBlank() ? null : category.strip();
    }
}
