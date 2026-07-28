package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 单条经验的 Feedback 统计摘要
 */
@Data
public class KnowledgeFeedbackSummaryVO {

    private Long knowledgeId;

    private Integer helpfulCount;

    private Integer usedCount;

    private Integer notHelpfulCount;

    private Integer outdatedCount;

    private Integer wrongCount;

    /** 反馈总数 */
    private Integer totalCount;

    /** helpful + used 占比 */
    private Double helpfulRate;
}
