package com.zhiyi.common;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> {

    /** 总记录数 */
    private long total;

    /** 当前页数据 */
    private List<T> list;

    /**
     * 构建分页结果
     */
    public static <T> PageResult<T> of(long total, List<T> list) {
        PageResult<T> pageResult = new PageResult<T>();
        pageResult.setTotal(total);
        pageResult.setList(list == null ? Collections.<T>emptyList() : list);
        return pageResult;
    }
}
