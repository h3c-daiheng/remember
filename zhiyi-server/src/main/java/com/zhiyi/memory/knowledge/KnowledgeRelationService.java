package com.zhiyi.memory.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.KnowledgeRelationCreateRequest;
import com.zhiyi.domain.vo.KnowledgeRelationVO;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeRelationEntity;
import com.zhiyi.memory.graph.GraphQueryService;
import com.zhiyi.memory.graph.RelationConstants;
import com.zhiyi.memory.graph.RelationEngine;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 经验关系服务：关系查询、人工建边与删除
 */
@Service
public class KnowledgeRelationService {

    private static final Set<String> MANUAL_RELATION_TYPES = new HashSet<String>(Arrays.asList(
            RelationConstants.TYPE_SAME_MODULE,
            RelationConstants.TYPE_SAME_TAG,
            RelationConstants.TYPE_RELATED_SEMANTIC,
            RelationConstants.TYPE_SAME_ARTIFACT,
            RelationConstants.TYPE_RELATED_DECISION,
            RelationConstants.TYPE_REFERENCES,
            RelationConstants.TYPE_DEPENDS_ON,
            RelationConstants.TYPE_SUPERSEDES));

    private final KnowledgeService knowledgeService;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final GraphQueryService graphQueryService;
    private final RelationEngine relationEngine;
    private final KnowledgeTimelineService knowledgeTimelineService;

    public KnowledgeRelationService(KnowledgeService knowledgeService,
                                    KnowledgeMapper knowledgeMapper,
                                    KnowledgeRelationMapper knowledgeRelationMapper,
                                    GraphQueryService graphQueryService,
                                    RelationEngine relationEngine,
                                    KnowledgeTimelineService knowledgeTimelineService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeRelationMapper = knowledgeRelationMapper;
        this.graphQueryService = graphQueryService;
        this.relationEngine = relationEngine;
        this.knowledgeTimelineService = knowledgeTimelineService;
    }

    /**
     * 查询指定经验的可见关系边，可按类型过滤
     */
    public List<KnowledgeRelationVO> listRelations(Long knowledgeId,
                                                 String workspaceId,
                                                 List<String> relationTypeList) {
        knowledgeService.getDetail(knowledgeId, workspaceId);
        List<KnowledgeRelationEntity> relationEntityList = graphQueryService.listVisibleRelations(
                knowledgeId, workspaceId, relationTypeList);
        List<KnowledgeRelationVO> relationViewList = new ArrayList<KnowledgeRelationVO>();
        for (KnowledgeRelationEntity relationEntity : relationEntityList) {
            relationViewList.add(toRelationView(relationEntity, knowledgeId));
        }
        return relationViewList;
    }

    /**
     * 汇总与指定经验相关的显式关系，供相关经验列表合并使用
     */
    public Map<Long, ExplicitRelationSummary> summarizeExplicitRelations(Long knowledgeId, String workspaceId) {
        List<KnowledgeRelationEntity> relationEntityList = graphQueryService.listVisibleRelations(
                knowledgeId, workspaceId, null);
        Map<Long, ExplicitRelationSummary> summaryMap = new HashMap<Long, ExplicitRelationSummary>();
        for (KnowledgeRelationEntity relationEntity : relationEntityList) {
            Long partnerId = knowledgeId.equals(relationEntity.getSourceId())
                    ? relationEntity.getTargetId() : relationEntity.getSourceId();
            KnowledgeEntity partnerEntity = knowledgeMapper.selectById(partnerId);
            if (partnerEntity == null
                    || !workspaceId.equals(partnerEntity.getWorkspaceId())
                    || partnerEntity.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
                continue;
            }
            ExplicitRelationSummary summary = summaryMap.get(partnerId);
            if (summary == null) {
                summary = new ExplicitRelationSummary();
                summary.setPartnerId(partnerId);
                summaryMap.put(partnerId, summary);
            }
            summary.getRelationTypes().add(relationEntity.getRelationType());
            summary.getRelationIds().add(relationEntity.getId());
            if (RelationConstants.RELATION_SOURCE_MANUAL.equals(relationEntity.getRelationSource())) {
                summary.setHasManualRelation(true);
            }
            double confidence = relationEntity.getConfidence() == null
                    ? 1D : relationEntity.getConfidence().doubleValue();
            if (confidence > summary.getMaxConfidence()) {
                summary.setMaxConfidence(confidence);
            }
        }
        return summaryMap;
    }

    /**
     * 人工创建关系边
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createManualRelation(Long sourceId,
                                     KnowledgeRelationCreateRequest createRequest,
                                     String workspaceId,
                                     Long creatorId) {
        if (createRequest == null || createRequest.getTargetId() == null) {
            throw new BusinessException(400, "targetId 不能为空");
        }
        validateRelationType(createRequest.getRelationType());
        if (sourceId.equals(createRequest.getTargetId())) {
            throw new BusinessException(400, "不能与自己建立关系");
        }

        knowledgeService.getDetail(sourceId, workspaceId);
        KnowledgeAggregate targetAggregate = knowledgeService.getDetail(createRequest.getTargetId(), workspaceId);
        if (targetAggregate.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
            throw new BusinessException(400, "仅可与已发布经验建边");
        }

        LambdaQueryWrapper<KnowledgeRelationEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        queryWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getSourceId, sourceId)
                .eq(KnowledgeRelationEntity::getTargetId, createRequest.getTargetId())
                .eq(KnowledgeRelationEntity::getRelationType, createRequest.getRelationType());
        KnowledgeRelationEntity existingEntity = knowledgeRelationMapper.selectOne(queryWrapper);
        if (existingEntity != null) {
            throw new BusinessException(400, "关系已存在");
        }

        KnowledgeRelationEntity relationEntity = new KnowledgeRelationEntity();
        relationEntity.setWorkspaceId(workspaceId);
        relationEntity.setSourceId(sourceId);
        relationEntity.setTargetId(createRequest.getTargetId());
        relationEntity.setRelationType(createRequest.getRelationType());
        relationEntity.setRelationSource(RelationConstants.RELATION_SOURCE_MANUAL);
        relationEntity.setConfidence(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
        relationEntity.setCreatorId(creatorId);
        knowledgeRelationMapper.insert(relationEntity);
        if (RelationConstants.TYPE_SUPERSEDES.equals(createRequest.getRelationType())) {
            knowledgeTimelineService.recordSupersede(sourceId, createRequest.getTargetId(), workspaceId, creatorId, null);
        }
        return relationEntity.getId();
    }

    /**
     * 删除关系边，需校验工作空间归属
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long relationId, String workspaceId) {
        KnowledgeRelationEntity relationEntity = knowledgeRelationMapper.selectById(relationId);
        if (relationEntity == null) {
            throw new BusinessException(404, "关系不存在");
        }
        if (!workspaceId.equals(relationEntity.getWorkspaceId())) {
            throw new BusinessException(403, "无权删除该关系");
        }
        knowledgeRelationMapper.deleteById(relationId);
    }

    /**
     * 删除经验时级联清理相关边
     */
    public void deleteRelationsByKnowledgeId(Long knowledgeId, String workspaceId) {
        relationEngine.deleteRelationsByKnowledgeId(knowledgeId, workspaceId);
    }

    private KnowledgeRelationVO toRelationView(KnowledgeRelationEntity relationEntity, Long currentKnowledgeId) {
        KnowledgeRelationVO relationView = new KnowledgeRelationVO();
        relationView.setId(relationEntity.getId());
        relationView.setSourceId(relationEntity.getSourceId());
        relationView.setTargetId(relationEntity.getTargetId());
        relationView.setRelationType(relationEntity.getRelationType());
        relationView.setRelationSource(relationEntity.getRelationSource());
        if (relationEntity.getConfidence() != null) {
            relationView.setConfidence(relationEntity.getConfidence().doubleValue());
        }
        relationView.setRelationMetadata(relationEntity.getRelationMetadata());
        relationView.setCreatorId(relationEntity.getCreatorId());

        Long partnerId = currentKnowledgeId.equals(relationEntity.getSourceId())
                ? relationEntity.getTargetId() : relationEntity.getSourceId();
        relationView.setPartnerId(partnerId);
        KnowledgeEntity partnerEntity = knowledgeMapper.selectById(partnerId);
        if (partnerEntity != null) {
            relationView.setPartnerTitle(partnerEntity.getTitle());
            relationView.setPartnerKnowledgeType(partnerEntity.getKnowledgeType());
            relationView.setPartnerModule(partnerEntity.getModule());
            relationView.setPartnerProject(partnerEntity.getProject());
        }
        return relationView;
    }

    private void validateRelationType(String relationType) {
        if (StringUtils.isBlank(relationType)) {
            throw new BusinessException(400, "relationType 不能为空");
        }
        if (!MANUAL_RELATION_TYPES.contains(relationType)) {
            throw new BusinessException(400, "relationType 不支持：" + relationType);
        }
    }

    /**
     * 显式关系汇总，供相关经验合并
     */
    public static class ExplicitRelationSummary {

        private Long partnerId;

        private final List<String> relationTypes = new ArrayList<String>();

        private final List<Long> relationIds = new ArrayList<Long>();

        private double maxConfidence;

        private boolean hasManualRelation;

        public Long getPartnerId() {
            return partnerId;
        }

        public void setPartnerId(Long partnerId) {
            this.partnerId = partnerId;
        }

        public List<String> getRelationTypes() {
            return relationTypes;
        }

        public List<Long> getRelationIds() {
            return relationIds;
        }

        public double getMaxConfidence() {
            return maxConfidence;
        }

        public void setMaxConfidence(double maxConfidence) {
            this.maxConfidence = maxConfidence;
        }

        public boolean isHasManualRelation() {
            return hasManualRelation;
        }

        public void setHasManualRelation(boolean hasManualRelation) {
            this.hasManualRelation = hasManualRelation;
        }
    }
}
