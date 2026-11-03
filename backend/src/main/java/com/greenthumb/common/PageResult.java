package com.greenthumb.common;

import java.util.List;

/**
 * 分页返回体。
 * 字段名与 frontend/src/mock/index.js 的 paged() 保持一致：{ list, total, page, size }
 */
public record PageResult<T>(List<T> list, long total, long page, long size) {

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list, total, page, size);
    }
}
