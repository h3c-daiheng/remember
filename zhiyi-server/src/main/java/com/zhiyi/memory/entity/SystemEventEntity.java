package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 统一 Event 实体，对应 system_event 表
 */
@Data
@TableName("system_event")
public class SystemEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属工作空间主键 */
    private String workspaceId;

    private String eventType;

    private String actor;

    private String workspace;

    private String repository;

    private String module;

    private Date eventTime;

    private String artifactsJson;

    private String metadataJson;

    private String payloadJson;

    private Integer processStatus;

    private Long knowledgeId;

    private Long creatorId;

    private Date createTime;

    private Date updateTime;
}
