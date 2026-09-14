package com.yuegang.zhihui.search.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductSearchRequest(@NotBlank @Size(max = 200) String keyword,
                                   @Min(1) @Max(100) int limit) {
    public ProductSearchRequest { keyword = keyword == null ? null : keyword.strip(); }
}
