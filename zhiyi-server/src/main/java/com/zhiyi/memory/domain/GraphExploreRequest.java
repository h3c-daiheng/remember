package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.List;

/**
 * Agent 图谱探索请求，供 MCP memory_graph_explore 调用
 */
@Data
public class GraphExploreRequest {

    /** 中心节点 knowledge.id */
    private Long centerId;

    /** 扩展跳数，默认 2，最大 3 */
    private Integer depth;

    /** 返回边数上限，默认 50 */
    private Integer limit;

    /** 关系类型过滤，空则返回全部可见边 */
    private List<String> relationTypes;
}
