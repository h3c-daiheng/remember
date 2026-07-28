package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 治理合并预览结果：供前端向导展示与编辑
 */
@Data
public class GovernanceMergePreviewView {

    /** 预览来源记忆 ID */
    private List<Long> sourceKnowledgeIds = new ArrayList<Long>();

    /** 合并后建议标题 */
    private String title;

    /** 合并后建议 Facts */
    private List<FactBlock> facts = new ArrayList<FactBlock>();

    /** 合并后建议标签 */
    private List<String> tags = new ArrayList<String>();

    /** 合并后建议 Artifacts */
    private List<ArtifactDto> artifacts = new ArrayList<ArtifactDto>();

    private String project;

    private String module;

    private String repository;

    private String knowledgeType;

    /** 预览生成方式：rule / llm / hybrid */
    private String previewSource;

    /** LLM 合并说明（可选） */
    private String mergeSummary;
}
