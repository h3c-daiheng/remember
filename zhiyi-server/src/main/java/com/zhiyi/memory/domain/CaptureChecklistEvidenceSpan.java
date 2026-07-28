package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * Review 检查项证据片段，供前端在 Fact Block 中高亮展示
 */
@Data
public class CaptureChecklistEvidenceSpan {

    /** 对应 facts 数组下标，从 0 开始 */
    private Integer factIndex;

    /** Fact 类型，如 decision、action */
    private String factType;

    /** 需高亮的文本片段 */
    private String snippet;

    /** 关联检查项编号，如 Q2、Q6 */
    private String checklistId;
}
