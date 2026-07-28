package com.zhiyi.common;

import lombok.Data;

/**
 * 分页查询通用参数
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 关键字模糊搜索 */
    private String keyword;

    /**
     * 获取安全的页码，避免空值或非法值
     */
    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /**
     * 获取安全的每页条数，限制上限防止一次拉取过多数据
     */
    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return pageSize > 100 ? 100 : pageSize;
    }
}
