package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 治理合并预览请求：可来自工单或手动多选记忆
 */
@Data
public class GovernanceMergePreviewRequest {

    /** 来源治理工单 ID，与 knowledgeIds 二选一 */
    private Long issueId;

    /** 手动选择的记忆 ID 列表 */
    private List<Long> knowledgeIds = new ArrayList<Long>();

    /** 是否启用 LLM 增强预览，默认 true */
    private Boolean useLlm;
}
