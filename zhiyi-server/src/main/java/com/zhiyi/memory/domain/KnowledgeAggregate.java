package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 知识聚合视图，包含 Facts、Artifacts 与标签
 */
@Data
public class KnowledgeAggregate {

    private Long id;

    private String knowledgeType;

    private String title;

    private String project;

    private String module;

    private String repository;

    private String language;

    private String framework;

    /** 0-草稿 1-已发布 2-已失效 */
    private Integer lifecycleStatus;

    private Integer recallCount;

    private Long creatorId;

    /** 提交人昵称（由 creatorId 关联 sys_user 解析） */
    private String creatorNickname;

    /** 提交人头像 URL */
    private String creatorAvatar;

    /** 所属工作空间主键 */
    private String workspaceId;

    /** 来源 Capture 草稿 ID */
    private Long sourceCaptureDraftId;

    private Date createTime;

    private Date updateTime;

    private List<String> tags;

    private List<FactBlock> facts;

    private List<ArtifactDto> artifacts;
}
