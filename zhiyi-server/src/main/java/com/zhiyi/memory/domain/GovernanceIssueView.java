package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 治理工单详情视图，含主版本与重复记忆摘要
 */
@Data
public class GovernanceIssueView {

    private Long id;

    private String issueType;

    private Integer status;

    private Long primaryKnowledgeId;

    private List<Long> relatedKnowledgeIds = new ArrayList<Long>();

    private String knowledgeType;

    private Double similarityScore;

    private String suggestedAction;

    private Long scanBatchId;

    /** 校验详情、反馈统计等扩展 */
    private String issueMetadataJson;

    private String resolvedAction;

    private String resolveComment;

    private Long operatorId;

    private Date createTime;

    private Date resolveTime;

    /** 主版本知识摘要 */
    private KnowledgeAggregate primaryKnowledge;

    /** 重复记忆摘要列表 */
    private List<KnowledgeAggregate> relatedKnowledgeList = new ArrayList<KnowledgeAggregate>();
}
