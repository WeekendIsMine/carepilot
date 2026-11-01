package com.carepilot.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页转换工具：把 MyBatis-Plus 的 Page 转成前端用的 PageResult。
 */
public final class Pages {

    public static final int MAX_SIZE = 100;

    private Pages() {}

    public static long page(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    public static long size(Integer size) {
        if (size == null || size < 1) return 10;
        return Math.min(size, MAX_SIZE);
    }

    public static <T> Page<T> of(Integer page, Integer size) {
        return new Page<>(page(page), size(size));
    }
}
