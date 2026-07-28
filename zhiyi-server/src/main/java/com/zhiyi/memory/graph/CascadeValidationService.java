package com.zhiyi.memory.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeRelationEntity;
import com.zhiyi.memory.entity.KnowledgeTimelineEntity;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.memory.timeline.TimelineConstants;
import com.zhiyi.memory.dao.KnowledgeTimelineMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 级联失效校验：经验下架时沿 depends_on 反向追溯，提示依赖方 Review
 */
@Service
public class CascadeValidationService {

    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeTimelineService knowledgeTimelineService;
    private final KnowledgeTimelineMapper knowledgeTimelineMapper;

    public CascadeValidationService(KnowledgeRelationMapper knowledgeRelationMapper,
                                    KnowledgeMapper knowledgeMapper,
                                    KnowledgeTimelineService knowledgeTimelineService,
                                    KnowledgeTimelineMapper knowledgeTimelineMapper) {
        this.knowledgeRelationMapper = knowledgeRelationMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeTimelineService = knowledgeTimelineService;
        this.knowledgeTimelineMapper = knowledgeTimelineMapper;
    }

    /**
     * 经验失效后创建级联 Review 提示，返回受影响的经验 ID 列表
     */
    public List<Long> createCascadeReviewHints(Long deprecatedKnowledgeId,
                                               String workspaceId,
                                               Long operatorId) {
        List<Long> dependentIdList = findReverseDependents(
                deprecatedKnowledgeId, workspaceId, TimelineConstants.CASCADE_REVIEW_MAX_DEPTH);
        if (dependentIdList.isEmpty()) {
            return dependentIdList;
        }

        for (Long dependentId : dependentIdList) {
            if (dependentId.equals(deprecatedKnowledgeId)) {
                continue;
            }
            if (hasRecentCascadeHint(dependentId, deprecatedKnowledgeId, workspaceId)) {
                continue;
            }
            knowledgeTimelineService.recordCascadeReviewHint(
                    dependentId, deprecatedKnowledgeId, workspaceId, operatorId);
        }
        return dependentIdList;
    }

    /**
     * 沿 depends_on 反向 BFS：找出所有直接或间接依赖指定经验且仍已发布的节点
     */
    public List<Long> findReverseDependents(Long knowledgeId, String workspaceId, int maxDepth) {
        if (knowledgeId == null || maxDepth <= 0) {
            return Collections.emptyList();
        }

        Set<Long> collectedIdSet = new HashSet<Long>();
        Set<Long> currentFrontier = new HashSet<Long>();
        currentFrontier.add(knowledgeId);

        for (int currentDepth = 0; currentDepth < maxDepth; currentDepth++) {
            Set<Long> nextFrontier = new HashSet<Long>();
            for (Long currentNodeId : currentFrontier) {
                List<Long> directDependentIdList = listDirectDependents(currentNodeId, workspaceId);
                for (Long dependentId : directDependentIdList) {
                    if (collectedIdSet.add(dependentId)) {
                        nextFrontier.add(dependentId);
                    }
                }
            }
            currentFrontier = nextFrontier;
            if (currentFrontier.isEmpty()) {
                break;
            }
        }
        return new ArrayList<Long>(collectedIdSet);
    }

    /**
     * 查询直接依赖 currentNodeId 的已发布经验（source depends_on target=currentNodeId）
     */
    private List<Long> listDirectDependents(Long currentNodeId, String workspaceId) {
        LambdaQueryWrapper<KnowledgeRelationEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        queryWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getTargetId, currentNodeId)
                .eq(KnowledgeRelationEntity::getRelationType, RelationConstants.TYPE_DEPENDS_ON);
        List<KnowledgeRelationEntity> relationEntityList = knowledgeRelationMapper.selectList(queryWrapper);

        List<Long> dependentIdList = new ArrayList<Long>();
        for (KnowledgeRelationEntity relationEntity : relationEntityList) {
            Long sourceId = relationEntity.getSourceId();
            if (sourceId == null || sourceId.equals(currentNodeId)) {
                continue;
            }
            KnowledgeEntity sourceEntity = knowledgeMapper.selectById(sourceId);
            if (sourceEntity == null
                    || !workspaceId.equals(sourceEntity.getWorkspaceId())
                    || sourceEntity.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
                continue;
            }
            dependentIdList.add(sourceId);
        }
        return dependentIdList;
    }

    /**
     * 避免同一依赖对在短时间重复写入级联提示
     */
    private boolean hasRecentCascadeHint(Long dependentId, Long deprecatedId, String workspaceId) {
        LambdaQueryWrapper<KnowledgeTimelineEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeTimelineEntity>();
        queryWrapper.eq(KnowledgeTimelineEntity::getKnowledgeId, dependentId)
                .eq(KnowledgeTimelineEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeTimelineEntity::getEventType, TimelineConstants.EVENT_CASCADE_REVIEW_HINT)
                .eq(KnowledgeTimelineEntity::getRelatedKnowledgeId, deprecatedId)
                .orderByDesc(KnowledgeTimelineEntity::getCreateTime)
                .last("LIMIT 1");
        KnowledgeTimelineEntity latestEntity = knowledgeTimelineMapper.selectOne(queryWrapper);
        return latestEntity != null;
    }
}
