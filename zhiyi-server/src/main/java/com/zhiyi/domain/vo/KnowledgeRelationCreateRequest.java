package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 人工建边请求
 */
@Data
public class KnowledgeRelationCreateRequest {

    /** 目标 knowledge.id */
    private Long targetId;

    /** 关系类型 */
    private String relationType;
}
