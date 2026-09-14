package com.yuegang.zhihui.common.core;

/**
 * One-based API pagination request with a hard upper bound.
 */
public record PageRequest(int pageNo, int pageSize) {

    public static final int DEFAULT_PAGE_NO = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public PageRequest {
        if (pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static PageRequest defaults() {
        return new PageRequest(DEFAULT_PAGE_NO, DEFAULT_PAGE_SIZE);
    }

    public long offset() {
        return Math.multiplyExact((long) pageNo - 1L, pageSize);
    }
}
