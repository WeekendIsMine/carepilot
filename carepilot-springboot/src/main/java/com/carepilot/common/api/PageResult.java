package com.carepilot.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应包装：总条数 + 当前页数据 + 页码信息。
 */
public record PageResult<T>(long total, long page, long size, List<T> records) {

    public static <T> PageResult<T> of(IPage<T> p) {
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }

    public static <E, T> PageResult<T> of(IPage<E> p, Function<E, T> mapper) {
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(),
                p.getRecords().stream().map(mapper).toList());
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(0, page, size, List.of());
    }
}
