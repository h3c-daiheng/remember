package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 图谱边视图
 */
@Data
public class GraphEdgeVO {

    private Long id;

    private Long sourceId;

    private Long targetId;

    private String relationType;

    private String relationSource;

    private Double confidence;

    private String relationMetadata;
}
