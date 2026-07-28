package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Date;

/**
 * AI Review 记录列表项：单次审查运行摘要 + 关联草稿标题/状态
 */
@Data
public class CaptureAiReviewListItem {

    private Long id;

    private Long draftId;

    /** 草稿标题（解析自 draft_json） */
    private String draftTitle;

    /** 0-排队 1-进行中 2-完成 3-失败 */
    private Integer status;

    /** approve / reject / route / escalate_human */
    private String decision;

    private Boolean similarHit;

    private Double confidence;

    private Boolean executed;

    private String errorMessage;

    private Integer latencyMs;

    private Date createTime;

    /**
     * 关联草稿审核状态（待确认时可「去确认/重跑」）
     */
    private Integer draftReviewStatus;
}
