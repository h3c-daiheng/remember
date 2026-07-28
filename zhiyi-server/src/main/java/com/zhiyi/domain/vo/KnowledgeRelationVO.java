package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 经验关系视图
 */
@Data
public class KnowledgeRelationVO {

    private Long id;

    private Long sourceId;

    private Long targetId;

    private String relationType;

    private String relationSource;

    private Double confidence;

    private String relationMetadata;

    private Long creatorId;

    /** 对端经验标题，便于前端展示 */
    private String partnerTitle;

    /** 对端经验 ID */
    private Long partnerId;

    /** 对端知识类型：experience / decision / rule / workflow */
    private String partnerKnowledgeType;

    /** 对端所属模块，关联决策展示同模块依据 */
    private String partnerModule;

    /** 对端所属项目 */
    private String partnerProject;
}
