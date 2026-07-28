package com.zhiyi.memory.governance;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 质量校验扫描单条结果，用于生成 incomplete / conflict 治理工单
 */
@Data
public class ValidationScanResult {

    /** 工单类型：incomplete / conflict */
    private String issueType;

    /** 主版本或冲突一方 knowledge.id */
    private Long primaryKnowledgeId;

    /** 关联 knowledge.id 列表（冲突时为另一方） */
    private List<Long> relatedKnowledgeIds = new ArrayList<Long>();

    private String knowledgeType;

    /** 不完整度或冲突置信度 0~1 */
    private double score;

    private String suggestedAction;

    /** 校验详情 JSON 字符串 */
    private String metadataJson;
}
