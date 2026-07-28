package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * Capture 归类时检索到的相似已有知识，辅助判断是否与 Rule 重复
 */
@Data
public class CaptureSimilarKnowledgeHint {

    /** 已有知识主键 */
    private Long knowledgeId;

    /** 知识类型：rule / decision / experience */
    private String knowledgeType;

    /** 知识标题 */
    private String title;

    /** 文本相似度 0~1 */
    private double similarityScore;
}
