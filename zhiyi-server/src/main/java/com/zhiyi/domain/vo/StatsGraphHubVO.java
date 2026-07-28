package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 图谱枢纽经验统计项：入度 + 出度高的核心节点
 */
@Data
public class StatsGraphHubVO {

    private Long id;

    private String title;

    private String module;

    private String project;

    /** 出度：作为 source 的边数 */
    private Integer outDegree;

    /** 入度：作为 target 的边数 */
    private Integer inDegree;

    /** 总度数 */
    private Integer totalDegree;

    private Integer recallCount;
}
