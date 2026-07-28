package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 被召回次数 Top N 经验项
 */
@Data
public class StatsTopKnowledgeVO {

    private Long id;

    private String title;

    /** Recall 累计调用次数 */
    private Integer recallCount;

    /** helpful / used 反馈次数 */
    private Integer helpfulCount;

    private String module;

    private String project;
}
