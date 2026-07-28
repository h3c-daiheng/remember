package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 经验版本时间线，对应 knowledge_timeline 表
 */
@Data
@TableName("knowledge_timeline")
public class KnowledgeTimelineEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作空间 */
    private String workspaceId;

    /** 关联 knowledge.id */
    private Long knowledgeId;

    /** 事件类型，见 TimelineConstants */
    private String eventType;

    /** 关联经验，如被替代者或级联源头 */
    private Long relatedKnowledgeId;

    /** 操作者 */
    private Long operatorId;

    /** 事件摘要 */
    private String eventSummary;

    /** 扩展信息 JSON */
    private String eventMetadata;

    private Date createTime;
}
