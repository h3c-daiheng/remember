package com.zhiyi.memory.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.KnowledgeSupersedeRequest;
import com.zhiyi.domain.vo.StatsGraphHubVO;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeRelationEntity;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图谱治理服务：替代演进、级联失效与枢纽节点统计
 */
@Service
public class GraphGovernanceService {

    private final KnowledgeService knowledgeService;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final KnowledgeTimelineService knowledgeTimelineService;
    private final GraphQueryService graphQueryService;

    public GraphGovernanceService(@Lazy KnowledgeService knowledgeService,
                                  KnowledgeMapper knowledgeMapper,
                                  KnowledgeRelationMapper knowledgeRelationMapper,
                                  KnowledgeTimelineService knowledgeTimelineService,
                                  GraphQueryService graphQueryService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeRelationMapper = knowledgeRelationMapper;
        this.knowledgeTimelineService = knowledgeTimelineService;
        this.graphQueryService = graphQueryService;
    }

    /**
     * 新版经验替代旧版：建立 supersedes 边、下架旧版并写入时间线
     */
    @Transactional(rollbackFor = Exception.class)
    public void supersede(Long successorId,
                          KnowledgeSupersedeRequest supersedeRequest,
                          String workspaceId,
                          Long operatorId,
                          String memberRole) {
        if (supersedeRequest == null || supersedeRequest.getPredecessorId() == null) {
            throw new BusinessException(400, "predecessorId 不能为空");
        }
        Long predecessorId = supersedeRequest.getPredecessorId();
        if (successorId.equals(predecessorId)) {
            throw new BusinessException(400, "不能替代自身");
        }

        KnowledgeEntity successorEntity = requirePublishedKnowledge(successorId, workspaceId);
        requirePublishedKnowledge(predecessorId, workspaceId);
        if (!WorkspaceMemberRole.canModifyKnowledge(memberRole, operatorId, successorEntity.getCreatorId())) {
            throw new BusinessException(403, "无权修改该经验");
        }

        createSupersedesRelation(successorId, predecessorId, workspaceId, operatorId);
        knowledgeTimelineService.recordSupersede(successorId, predecessorId, workspaceId, operatorId,
                supersedeRequest.getComment());

        knowledgeService.deprecate(predecessorId, workspaceId, operatorId, memberRole);
    }

    /**
     * 统计工作空间内图谱枢纽节点：按入度 + 出度排序
     */
    public List<StatsGraphHubVO> listGraphHubs(String workspaceId, Integer limit) {
        int safeLimit = normalizeHubLimit(limit);

        LambdaQueryWrapper<KnowledgeRelationEntity> relationQuery = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        relationQuery.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId);
        List<KnowledgeRelationEntity> relationEntityList = knowledgeRelationMapper.selectList(relationQuery);
        if (relationEntityList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, DegreeCounter> degreeMap = new HashMap<Long, DegreeCounter>();
        for (KnowledgeRelationEntity relationEntity : relationEntityList) {
            if (!graphQueryService.isVisibleRelation(relationEntity)) {
                continue;
            }
            incrementOutDegree(degreeMap, relationEntity.getSourceId());
            incrementInDegree(degreeMap, relationEntity.getTargetId());
        }

        List<Long> rankedIdList = new ArrayList<Long>(degreeMap.keySet());
        Collections.sort(rankedIdList, new Comparator<Long>() {
            @Override
            public int compare(Long leftId, Long rightId) {
                DegreeCounter leftCounter = degreeMap.get(leftId);
                DegreeCounter rightCounter = degreeMap.get(rightId);
                int leftTotal = leftCounter.getInDegree() + leftCounter.getOutDegree();
                int rightTotal = rightCounter.getInDegree() + rightCounter.getOutDegree();
                if (leftTotal != rightTotal) {
                    return rightTotal - leftTotal;
                }
                return Long.compare(leftId, rightId);
            }
        });

        List<StatsGraphHubVO> hubViewList = new ArrayList<StatsGraphHubVO>();
        for (Long knowledgeId : rankedIdList) {
            if (hubViewList.size() >= safeLimit) {
                break;
            }
            KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
            if (entity == null
                    || !workspaceId.equals(entity.getWorkspaceId())
                    || entity.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
                continue;
            }
            DegreeCounter counter = degreeMap.get(knowledgeId);
            StatsGraphHubVO hubView = new StatsGraphHubVO();
            hubView.setId(entity.getId());
            hubView.setTitle(entity.getTitle());
            hubView.setModule(entity.getModule());
            hubView.setProject(entity.getProject());
            hubView.setInDegree(counter.getInDegree());
            hubView.setOutDegree(counter.getOutDegree());
            hubView.setTotalDegree(counter.getInDegree() + counter.getOutDegree());
            hubView.setRecallCount(entity.getRecallCount());
            hubViewList.add(hubView);
        }
        return hubViewList;
    }

    private void createSupersedesRelation(Long successorId,
                                          Long predecessorId,
                                          String workspaceId,
                                          Long operatorId) {
        LambdaQueryWrapper<KnowledgeRelationEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        queryWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getSourceId, successorId)
                .eq(KnowledgeRelationEntity::getTargetId, predecessorId)
                .eq(KnowledgeRelationEntity::getRelationType, RelationConstants.TYPE_SUPERSEDES);
        KnowledgeRelationEntity existingEntity = knowledgeRelationMapper.selectOne(queryWrapper);
        if (existingEntity != null) {
            return;
        }

        KnowledgeRelationEntity relationEntity = new KnowledgeRelationEntity();
        relationEntity.setWorkspaceId(workspaceId);
        relationEntity.setSourceId(successorId);
        relationEntity.setTargetId(predecessorId);
        relationEntity.setRelationType(RelationConstants.TYPE_SUPERSEDES);
        relationEntity.setRelationSource(RelationConstants.RELATION_SOURCE_MANUAL);
        relationEntity.setConfidence(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
        relationEntity.setCreatorId(operatorId);
        knowledgeRelationMapper.insert(relationEntity);
    }

    private KnowledgeEntity requirePublishedKnowledge(Long knowledgeId, String workspaceId) {
        KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
        if (entity == null || !workspaceId.equals(entity.getWorkspaceId())) {
            throw new BusinessException(404, "经验不存在");
        }
        if (entity.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
            throw new BusinessException(400, "仅已发布经验可参与替代演进");
        }
        return entity;
    }

    private void incrementOutDegree(Map<Long, DegreeCounter> degreeMap, Long knowledgeId) {
        if (knowledgeId == null) {
            return;
        }
        DegreeCounter counter = degreeMap.get(knowledgeId);
        if (counter == null) {
            counter = new DegreeCounter();
            degreeMap.put(knowledgeId, counter);
        }
        counter.setOutDegree(counter.getOutDegree() + 1);
    }

    private void incrementInDegree(Map<Long, DegreeCounter> degreeMap, Long knowledgeId) {
        if (knowledgeId == null) {
            return;
        }
        DegreeCounter counter = degreeMap.get(knowledgeId);
        if (counter == null) {
            counter = new DegreeCounter();
            degreeMap.put(knowledgeId, counter);
        }
        counter.setInDegree(counter.getInDegree() + 1);
    }

    private int normalizeHubLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    /**
     * 图谱度数计数器
     */
    private static class DegreeCounter {

        private int inDegree;

        private int outDegree;

        public int getInDegree() {
            return inDegree;
        }

        public void setInDegree(int inDegree) {
            this.inDegree = inDegree;
        }

        public int getOutDegree() {
            return outDegree;
        }

        public void setOutDegree(int outDegree) {
            this.outDegree = outDegree;
        }
    }
}
