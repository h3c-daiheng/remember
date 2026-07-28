package com.zhiyi.memory.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.GraphEdgeVO;
import com.zhiyi.domain.vo.GraphNodeVO;
import com.zhiyi.domain.vo.GraphViewVO;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeRelationEntity;
import com.zhiyi.memory.knowledge.KnowledgeService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 图谱查询服务：以中心节点 BFS 扩展子图，供 /graph API 与后续可视化使用
 */
@Service
public class GraphQueryService {

    private final KnowledgeService knowledgeService;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;

    public GraphQueryService(KnowledgeService knowledgeService,
                             KnowledgeMapper knowledgeMapper,
                             KnowledgeRelationMapper knowledgeRelationMapper) {
        this.knowledgeService = knowledgeService;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeRelationMapper = knowledgeRelationMapper;
    }

    /**
     * 查询以 centerId 为中心的子图，按 depth 层 BFS 扩展
     */
    public GraphViewVO querySubGraph(Long centerId,
                                     String workspaceId,
                                     int depth,
                                     int limit,
                                     List<String> relationTypeList) {
        int safeDepth = normalizeDepth(depth);
        int safeLimit = normalizeLimit(limit);
        knowledgeService.getDetail(centerId, workspaceId);

        Set<Long> visitedNodeIds = new HashSet<Long>();
        Set<Long> collectedEdgeIds = new HashSet<Long>();
        List<GraphEdgeVO> edgeViewList = new ArrayList<GraphEdgeVO>();
        Map<Long, Boolean> publishedCache = new HashMap<Long, Boolean>();
        Queue<Long> frontierQueue = new LinkedList<Long>();
        frontierQueue.offer(centerId);
        visitedNodeIds.add(centerId);

        for (int currentDepth = 0; currentDepth < safeDepth; currentDepth++) {
            if (frontierQueue.isEmpty()) {
                break;
            }
            int levelSize = frontierQueue.size();
            for (int index = 0; index < levelSize; index++) {
                Long currentNodeId = frontierQueue.poll();
                List<KnowledgeRelationEntity> relationEntityList = listVisibleRelations(
                        currentNodeId, workspaceId, relationTypeList);
                for (KnowledgeRelationEntity relationEntity : relationEntityList) {
                    if (collectedEdgeIds.contains(relationEntity.getId())) {
                        continue;
                    }
                    collectedEdgeIds.add(relationEntity.getId());
                    edgeViewList.add(toEdgeView(relationEntity));
                    if (edgeViewList.size() >= safeLimit) {
                        break;
                    }

                    Long neighborId = resolveNeighborId(currentNodeId, relationEntity);
                    if (neighborId == null || !isPublishedKnowledge(neighborId, workspaceId, publishedCache)) {
                        continue;
                    }
                    if (visitedNodeIds.add(neighborId)) {
                        frontierQueue.offer(neighborId);
                    }
                }
                if (edgeViewList.size() >= safeLimit) {
                    break;
                }
            }
            if (edgeViewList.size() >= safeLimit) {
                break;
            }
        }

        GraphViewVO graphView = new GraphViewVO();
        graphView.setCenterId(centerId);
        graphView.setDepth(safeDepth);
        List<GraphNodeVO> nodeViewList = buildNodeViews(visitedNodeIds, workspaceId);
        graphView.setNodes(nodeViewList);
        graphView.setEdges(filterEdgesByPublishedNodes(edgeViewList, nodeViewList, safeLimit));
        return graphView;
    }

    /**
     * 仅保留两端节点均为已发布经验的边，避免图谱出现指向已下架节点的悬空边
     */
    private List<GraphEdgeVO> filterEdgesByPublishedNodes(List<GraphEdgeVO> edgeViewList,
                                                          List<GraphNodeVO> nodeViewList,
                                                          int safeLimit) {
        if (edgeViewList.isEmpty()) {
            return edgeViewList;
        }
        Set<Long> publishedNodeIdSet = new HashSet<Long>();
        for (GraphNodeVO nodeView : nodeViewList) {
            publishedNodeIdSet.add(nodeView.getId());
        }
        List<GraphEdgeVO> visibleEdgeList = new ArrayList<GraphEdgeVO>();
        for (GraphEdgeVO edgeView : edgeViewList) {
            if (publishedNodeIdSet.contains(edgeView.getSourceId())
                    && publishedNodeIdSet.contains(edgeView.getTargetId())) {
                visibleEdgeList.add(edgeView);
            }
            if (visibleEdgeList.size() >= safeLimit) {
                break;
            }
        }
        return visibleEdgeList;
    }

    /**
     * 从多个种子节点 BFS 扩展，返回各节点相对种子的最短跳数（种子为 0 跳）
     */
    public Map<Long, Integer> expandHopDistances(Set<Long> seedIdSet,
                                                 String workspaceId,
                                                 int depth,
                                                 List<String> relationTypeList) {
        Map<Long, Integer> hopDistanceMap = new HashMap<Long, Integer>();
        if (seedIdSet == null || seedIdSet.isEmpty()) {
            return hopDistanceMap;
        }
        int safeDepth = normalizeDepth(depth);
        Set<Long> currentFrontier = new HashSet<Long>(seedIdSet);
        Map<Long, Boolean> publishedCache = new HashMap<Long, Boolean>();
        for (Long seedId : seedIdSet) {
            hopDistanceMap.put(seedId, 0);
        }

        for (int currentHop = 0; currentHop < safeDepth; currentHop++) {
            Set<Long> nextFrontier = new HashSet<Long>();
            for (Long currentNodeId : currentFrontier) {
                List<KnowledgeRelationEntity> relationEntityList = listVisibleRelations(
                        currentNodeId, workspaceId, relationTypeList);
                for (KnowledgeRelationEntity relationEntity : relationEntityList) {
                    Long neighborId = resolveNeighborId(currentNodeId, relationEntity);
                    if (neighborId == null || hopDistanceMap.containsKey(neighborId)) {
                        continue;
                    }
                    if (!isPublishedKnowledge(neighborId, workspaceId, publishedCache)) {
                        continue;
                    }
                    hopDistanceMap.put(neighborId, currentHop + 1);
                    nextFrontier.add(neighborId);
                }
            }
            currentFrontier = nextFrontier;
            if (currentFrontier.isEmpty()) {
                break;
            }
        }
        return hopDistanceMap;
    }

    /**
     * 查询与节点相连且满足展示条件的边
     */
    public List<KnowledgeRelationEntity> listVisibleRelations(Long knowledgeId,
                                                              String workspaceId,
                                                              List<String> relationTypeList) {
        LambdaQueryWrapper<KnowledgeRelationEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        queryWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .and(wrapper -> wrapper.eq(KnowledgeRelationEntity::getSourceId, knowledgeId)
                        .or().eq(KnowledgeRelationEntity::getTargetId, knowledgeId));
        if (relationTypeList != null && !relationTypeList.isEmpty()) {
            queryWrapper.in(KnowledgeRelationEntity::getRelationType, relationTypeList);
        }
        List<KnowledgeRelationEntity> relationEntityList = knowledgeRelationMapper.selectList(queryWrapper);
        List<KnowledgeRelationEntity> visibleList = new ArrayList<KnowledgeRelationEntity>();
        for (KnowledgeRelationEntity relationEntity : relationEntityList) {
            if (isVisibleRelation(relationEntity)) {
                visibleList.add(relationEntity);
            }
        }
        return visibleList;
    }

    /**
     * 自动边需满足置信度阈值，人工边始终展示
     */
    public boolean isVisibleRelation(KnowledgeRelationEntity relationEntity) {
        if (RelationConstants.RELATION_SOURCE_MANUAL.equals(relationEntity.getRelationSource())
                || RelationConstants.RELATION_SOURCE_MERGE.equals(relationEntity.getRelationSource())) {
            return true;
        }
        if (relationEntity.getConfidence() == null) {
            return false;
        }
        return relationEntity.getConfidence().doubleValue() >= RelationConstants.AUTO_EDGE_DISPLAY_CONFIDENCE;
    }

    private List<GraphNodeVO> buildNodeViews(Set<Long> nodeIdSet, String workspaceId) {
        if (nodeIdSet.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<KnowledgeEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        queryWrapper.in(KnowledgeEntity::getId, nodeIdSet)
                .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED);
        List<KnowledgeEntity> entityList = knowledgeMapper.selectList(queryWrapper);

        List<GraphNodeVO> nodeViewList = new ArrayList<GraphNodeVO>();
        for (KnowledgeEntity entity : entityList) {
            GraphNodeVO nodeView = new GraphNodeVO();
            nodeView.setId(entity.getId());
            nodeView.setTitle(entity.getTitle());
            nodeView.setKnowledgeType(entity.getKnowledgeType());
            nodeView.setProject(entity.getProject());
            nodeView.setModule(entity.getModule());
            nodeView.setRecallCount(entity.getRecallCount());
            nodeViewList.add(nodeView);
        }
        return nodeViewList;
    }

    private GraphEdgeVO toEdgeView(KnowledgeRelationEntity relationEntity) {
        GraphEdgeVO edgeView = new GraphEdgeVO();
        edgeView.setId(relationEntity.getId());
        edgeView.setSourceId(relationEntity.getSourceId());
        edgeView.setTargetId(relationEntity.getTargetId());
        edgeView.setRelationType(relationEntity.getRelationType());
        edgeView.setRelationSource(relationEntity.getRelationSource());
        if (relationEntity.getConfidence() != null) {
            edgeView.setConfidence(relationEntity.getConfidence().doubleValue());
        }
        edgeView.setRelationMetadata(relationEntity.getRelationMetadata());
        return edgeView;
    }

    private boolean isPublishedKnowledge(Long knowledgeId, String workspaceId, Map<Long, Boolean> publishedCache) {
        Boolean cachedPublished = publishedCache.get(knowledgeId);
        if (cachedPublished != null) {
            return cachedPublished.booleanValue();
        }
        KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
        boolean published = entity != null
                && workspaceId.equals(entity.getWorkspaceId())
                && entity.getLifecycleStatus() == MemoryConstants.LIFECYCLE_PUBLISHED;
        publishedCache.put(knowledgeId, published);
        return published;
    }

    private Long resolveNeighborId(Long currentNodeId, KnowledgeRelationEntity relationEntity) {
        if (currentNodeId.equals(relationEntity.getSourceId())) {
            return relationEntity.getTargetId();
        }
        if (currentNodeId.equals(relationEntity.getTargetId())) {
            return relationEntity.getSourceId();
        }
        return null;
    }

    private int normalizeDepth(int depth) {
        if (depth <= 0) {
            return 1;
        }
        if (depth > 3) {
            throw new BusinessException(400, "depth 最大为 3");
        }
        return depth;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 50;
        }
        if (limit > 200) {
            throw new BusinessException(400, "limit 最大为 200");
        }
        return limit;
    }
}
