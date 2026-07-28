package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 统一知识对象，对应 knowledge 表
 */
@Data
@TableName("knowledge")
public class KnowledgeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属工作空间，经验隔离边界 */
    private String workspaceId;

    private String knowledgeType;

    private String title;

    private String project;

    private String module;

    private String repository;

    private String language;

    private String framework;

    private Integer lifecycleStatus;

    private Integer recallCount;

    private Long creatorId;

    /** 来源 Capture 草稿 ID，便于从知识反查 Remember 链路 */
    private Long sourceCaptureDraftId;

    @TableLogic
    private Integer deleted;

    private Date createTime;

    private Date updateTime;
}
