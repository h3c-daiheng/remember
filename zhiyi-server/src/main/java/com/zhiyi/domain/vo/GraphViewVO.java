package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 图谱子图视图
 */
@Data
public class GraphViewVO {

    private Long centerId;

    private Integer depth;

    private List<GraphNodeVO> nodes;

    private List<GraphEdgeVO> edges;
}
