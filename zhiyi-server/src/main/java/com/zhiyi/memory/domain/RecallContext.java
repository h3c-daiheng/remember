package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.List;

/**
 * Recall / Search 统一上下文对象，驱动召回与排序
 */
@Data
public class RecallContext {

    /** 当前任务描述 */
    private String task;

    /** 用户当前输入的 prompt 原文 */
    private String currentPrompt;

    /** 工作空间编码，与 RecallContext.workspace 对齐 */
    private String workspace;

    /** 工作空间主键，Recall 数据隔离使用 */
    private String workspaceId;

    /** 代码仓库 */
    private String repository;

    /** 项目 */
    private String project;

    /** 模块 */
    private String module;

    /** 分支 */
    private String branch;

    /** 编程语言 */
    private String language;

    /** 框架 */
    private String framework;

    /** 当前文件 */
    private String currentFile;

    /** 修改过的文件列表 */
    private List<String> modifiedFiles;

    /** 依赖列表 */
    private List<String> dependencies;

    /** 关联 Issue */
    private String issue;

    /** 角色 */
    private String role;

    /** 标签过滤 */
    private List<String> tags;

    /** 需要返回的 Fact 类型 */
    private List<String> factTypes;

    /** 需要召回的知识类型：rule / workflow / experience，不传则默认仅 experience */
    private List<String> knowledgeTypes;

    /** 返回条数上限 */
    private Integer limit;

    /** 是否沿图谱扩展 Recall 候选（V3 Hybrid Recall） */
    private Boolean expandGraph;

    /** 图谱扩展跳数，默认 2 */
    private Integer graphDepth;

    /** 图谱扩展使用的关系类型，未传时使用 depends_on / related_semantic / related_decision */
    private List<String> graphRelationTypes;
}
