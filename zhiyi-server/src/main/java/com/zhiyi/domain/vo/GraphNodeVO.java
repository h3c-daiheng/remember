package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 图谱节点视图
 */
@Data
public class GraphNodeVO {

    private Long id;

    private String title;

    private String knowledgeType;

    private String project;

    private String module;

    private Integer recallCount;
}
