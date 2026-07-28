package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 治理合并确认请求：基于预览结果发布新版并替代源记忆
 */
@Data
public class GovernanceMergeConfirmRequest {

    /** 关联治理工单 ID，可选 */
    private Long issueId;

    /** 源记忆 ID 列表，须与预览一致 */
    private List<Long> sourceKnowledgeIds = new ArrayList<Long>();

    /** 合并后标题 */
    private String title;

    /** 合并后 Facts */
    private List<FactBlock> facts = new ArrayList<FactBlock>();

    /** 合并后标签 */
    private List<String> tags = new ArrayList<String>();

    /** 合并后 Artifacts */
    private List<ArtifactDto> artifacts = new ArrayList<ArtifactDto>();

    private String project;

    private String module;

    private String repository;

    /** 知识类型，默认 experience */
    private String knowledgeType;

    /** 处置备注 */
    private String comment;
}
