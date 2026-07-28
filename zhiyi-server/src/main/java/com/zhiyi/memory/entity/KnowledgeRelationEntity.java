package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 经验关系边，对应 knowledge_relation 表
 */
@Data
@TableName("knowledge_relation")
public class KnowledgeRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作空间，经验隔离边界 */
    private String workspaceId;

    /** 源 knowledge.id */
    private Long sourceId;

    /** 目标 knowledge.id */
    private Long targetId;

    /** 关系类型，如 same_module / related_semantic */
    private String relationType;

    /** 建边来源：auto / manual / merge */
    private String relationSource;

    /** 自动建边置信度 0~1 */
    private BigDecimal confidence;

    /** 建边依据快照 JSON */
    private String relationMetadata;

    /** 人工建边时的操作者 */
    private Long creatorId;

    private Date createTime;
}
