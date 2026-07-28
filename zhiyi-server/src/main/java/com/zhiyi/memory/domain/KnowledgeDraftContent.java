package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.List;

/**
 * Capture 草稿内容，包含 Facts、Artifacts 与元数据
 */
@Data
public class KnowledgeDraftContent {

    /** 标题 */
    private String title;

    /** 项目 */
    private String project;

    /** 模块 */
    private String module;

    /** 仓库 */
    private String repository;

    /** 编程语言 */
    private String language;

    /** 框架 */
    private String framework;

    /** 标签 */
    private List<String> tags;

    /** Fact Block 列表 */
    private List<FactBlock> facts;

    /** Artifact 列表 */
    private List<ArtifactDto> artifacts;

    /**
     * Agent 提交时声明的知识类型，Review 页默认按此类型确认
     */
    private String submittedKnowledgeType;
}
