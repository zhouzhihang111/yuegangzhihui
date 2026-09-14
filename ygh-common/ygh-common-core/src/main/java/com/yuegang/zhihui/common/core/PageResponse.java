package com.yuegang.zhihui.common.core;

import java.util.List;
import java.util.Objects;

/**
 * Stable page payload returned inside {@link ApiResponse}.
 */
public record PageResponse<T>(
        List<T> records,
        int pageNo,
        int pageSize,
        long total,
        long pages) {

    public PageResponse {
        records = List.copyOf(Objects.requireNonNull(records, "records must not be null"));
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageSize < 1 || pageSize > PageRequest.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize is outside the supported range");
        }
        if (total < 0 || pages < 0) {
            throw new IllegalArgumentException("total and pages must not be negative");
        }
    }

    public static <T> PageResponse<T> of(List<T> records, PageRequest request, long total) {
        Objects.requireNonNull(request, "request must not be null");
        var pages = total == 0 ? 0 : ((total - 1) / request.pageSize()) + 1;
        return new PageResponse<>(records, request.pageNo(), request.pageSize(), total, pages);
    }
}
