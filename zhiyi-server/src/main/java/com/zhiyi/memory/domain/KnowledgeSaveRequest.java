package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 创建或更新知识的请求体
 */
@Data
public class KnowledgeSaveRequest {

    private String title;

    private String project;

    private String module;

    private String repository;

    private String language;

    private String framework;

    private List<String> tags;

    private List<FactBlock> facts;

    private List<ArtifactDto> artifacts;

    /** 是否创建后直接发布 */
    private Boolean publish;

    /** 知识类型：experience / rule / workflow，默认 experience */
    private String knowledgeType;
}
