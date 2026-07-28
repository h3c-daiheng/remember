package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 统一 Event 请求体，Remember API 的输入
 */
@Data
public class SystemEventRequest {

    /** 事件类型：agent_finished / manual_create */
    private String type;

    /** 触发者 */
    private String actor;

    /** 工作空间编码 */
    private String workspace;

    /** 工作空间主键，由鉴权上下文注入 */
    private String workspaceId;

    /** 仓库 */
    private String repository;

    /** 业务项目名（Recall 筛选用，与工作空间编码 workspace 无关） */
    private String project;

    /** 模块 */
    private String module;

    /** 事件时间 */
    private Date time;

    /** 统一 artifacts 数组 */
    private List<ArtifactDto> artifacts;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    /** 业务载荷 */
    private Map<String, Object> payload;

    /**
     * Agent 声明的知识类型：experience / rule / workflow / decision
     * 未传时默认 experience，全部进入 Capture 草稿待人工确认
     */
    private String knowledgeType;
}
