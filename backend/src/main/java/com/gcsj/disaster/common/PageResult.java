package com.gcsj.disaster.common;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应包装
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int page;       // 1-based
    private int size;
    private int totalPages;

    public static <S, T> PageResult<T> from(Page<S> springPage, Function<S, T> mapper) {
        PageResult<T> r = new PageResult<>();
        r.records = springPage.getContent().stream().map(mapper).toList();
        r.total = springPage.getTotalElements();
        r.page = springPage.getNumber() + 1;
        r.size = springPage.getSize();
        r.totalPages = springPage.getTotalPages();
        return r;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        PageResult<T> r = new PageResult<>();
        r.records = records;
        r.total = total;
        r.page = page;
        r.size = size;
        r.totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return r;
    }
}
