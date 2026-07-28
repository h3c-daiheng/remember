package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.Date;

/**
 * 经验时间线单条记录视图
 */
@Data
public class KnowledgeTimelineItemVO {

    private Long id;

    private Long knowledgeId;

    /** 事件类型 */
    private String eventType;

    /** 关联经验 ID */
    private Long relatedKnowledgeId;

    /** 关联经验标题 */
    private String relatedKnowledgeTitle;

    private Long operatorId;

    private String eventSummary;

    private String eventMetadata;

    private Date createTime;
}
