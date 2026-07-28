package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * AI Review 记录详情：完整审查结果 + 草稿摘要（供独立记录页抽屉展示）
 */
@Data
public class CaptureAiReviewDetailView {

    /** 单次审查完整视图 */
    private CaptureAiReviewView review;

    /** 草稿标题 */
    private String draftTitle;

    /** 关联草稿审核状态 */
    private Integer draftReviewStatus;

    private Long submitterId;

    private String submitterNickname;

    private String submitterAvatar;
}
