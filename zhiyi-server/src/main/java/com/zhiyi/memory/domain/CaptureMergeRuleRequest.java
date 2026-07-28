package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * Capture 合并到已有 Rule 请求
 */
@Data
public class CaptureMergeRuleRequest {

    /** 目标 Rule 的 knowledge 主键 */
    private Long targetKnowledgeId;

    /** 拒绝码，通常为 REJECT_DUPLICATE_RULE */
    private String rejectReason;

    /** Review 备注 */
    private String reviewComment;

    /** Review 人编辑后的草稿内容，可选 */
    private KnowledgeDraftContent editedContent;
}
