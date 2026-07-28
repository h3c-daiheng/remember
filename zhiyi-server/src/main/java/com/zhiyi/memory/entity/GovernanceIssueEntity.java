package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 记忆治理工单，对应 governance_issue 表
 */
@Data
@TableName("governance_issue")
public class GovernanceIssueEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作空间主键 */
    private String workspaceId;

    /** 工单类型：duplicate 等 */
    private String issueType;

    /** 工单状态：0 待处理 / 1 已解决 / 2 已忽略 */
    private Integer status;

    /** 推荐保留的主版本 knowledge.id */
    private Long primaryKnowledgeId;

    /** 关联重复记忆 ID 列表 JSON */
    private String relatedKnowledgeIds;

    /** 知识类型 */
    private String knowledgeType;

    /** 组内最高相似度 */
    private Double similarityScore;

    /** 系统建议处置动作 */
    private String suggestedAction;

    /** 来源扫描批次 */
    private Long scanBatchId;

    /** 校验详情、反馈统计等扩展 JSON */
    private String issueMetadataJson;

    /** 实际处置动作 */
    private String resolvedAction;

    /** 处置备注 */
    private String resolveComment;

    /** 处置操作者 */
    private Long operatorId;

    private Date createTime;

    private Date updateTime;

    private Date resolveTime;
}
