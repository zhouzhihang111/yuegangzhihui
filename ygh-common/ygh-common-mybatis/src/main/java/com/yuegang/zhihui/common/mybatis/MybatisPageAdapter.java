package com.yuegang.zhihui.common.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuegang.zhihui.common.core.PageRequest;
import com.yuegang.zhihui.common.core.PageResponse;
import java.util.Objects;
import java.util.function.Function;

/** Converts between persistence paging types and the stable external page contract. */
public final class MybatisPageAdapter {

    private MybatisPageAdapter() {
    }

    public static <T> Page<T> toMybatisPage(PageRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new Page<>(request.pageNo(), request.pageSize(), true);
    }

    public static <S, T> PageResponse<T> toPageResponse(
            IPage<S> page,
            Function<? super S, T> mapper
    ) {
        Objects.requireNonNull(page, "page must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");
        Objects.requireNonNull(page.getRecords(), "page records must not be null");

        int pageNo = Math.toIntExact(page.getCurrent());
        int pageSize = Math.toIntExact(page.getSize());
        var request = new PageRequest(pageNo, pageSize);
        var records = page.getRecords().stream().map(mapper).toList();
        return PageResponse.of(records, request, page.getTotal());
    }
}
