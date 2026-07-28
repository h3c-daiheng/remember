package com.zhiyi.memory.retrieval;

import com.zhiyi.memory.graph.GraphQueryService;
import com.zhiyi.memory.graph.RelationConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图谱检索客户端：从向量种子节点沿关系边 BFS 扩展，供 Hybrid Recall 使用
 */
@Component
public class GraphRetrievalClient {

    private final GraphQueryService graphQueryService;

    public GraphRetrievalClient(GraphQueryService graphQueryService) {
        this.graphQueryService = graphQueryService;
    }

    /**
     * 从多个种子节点出发，计算 workspace 内各节点的最短跳数（不含种子自身为 0 跳）
     */
    public Map<Long, Integer> expandFromSeeds(Set<Long> seedIdSet,
                                              String workspaceId,
                                              int depth,
                                              List<String> relationTypeList) {
        if (seedIdSet == null || seedIdSet.isEmpty()) {
            return Collections.emptyMap();
        }
        return graphQueryService.expandHopDistances(seedIdSet, workspaceId, depth, relationTypeList);
    }

    /**
     * 解析 Recall 图谱扩展使用的关系类型；未指定时使用 V3 默认三类边
     */
    public List<String> resolveRecallRelationTypes(List<String> requestedTypeList) {
        if (requestedTypeList != null && !requestedTypeList.isEmpty()) {
            return requestedTypeList;
        }
        List<String> defaultTypeList = new ArrayList<String>();
        defaultTypeList.add(RelationConstants.TYPE_DEPENDS_ON);
        defaultTypeList.add(RelationConstants.TYPE_RELATED_SEMANTIC);
        defaultTypeList.add(RelationConstants.TYPE_RELATED_DECISION);
        return defaultTypeList;
    }

    /**
     * 根据跳数计算 graphProximity 因子：1 / (1 + hopDistance)
     */
    public double calculateGraphProximity(int hopDistance) {
        if (hopDistance < 0) {
            return 1.0D;
        }
        return 1.0D / (1.0D + hopDistance);
    }
}
