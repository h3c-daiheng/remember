package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 标签维度经验与反馈分布项
 */
@Data
public class StatsTagItemVO {

    private String tagName;

    private Integer knowledgeCount;

    /** 近窗口内 helpful / used 反馈数 */
    private Integer helpfulCount;
}
