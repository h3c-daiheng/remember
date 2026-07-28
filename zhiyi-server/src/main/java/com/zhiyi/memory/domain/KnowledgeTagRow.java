package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 工作空间内知识标签行，供统计聚合使用
 */
@Data
public class KnowledgeTagRow {

    private Long knowledgeId;

    private String tagName;
}
