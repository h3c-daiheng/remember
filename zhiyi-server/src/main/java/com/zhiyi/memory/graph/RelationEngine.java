package com.zhiyi.memory.graph;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.KnowledgeTagRow;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeRelationEntity;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.retrieval.RetrievalEngine;
import org.springframework.context.annotation.Lazy;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
 * 关系建边引擎：发布经验后按规则与语义相似度自动写入 knowledge_relation
 */
@Component
public class RelationEngine {

    private static final Logger log = LoggerFactory.getLogger(RelationEngine.class);

    private final KnowledgeService knowledgeService;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final KnowledgeArtifactMapper knowledgeArtifactMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final RetrievalEngine retrievalEngine;

    public RelationEngine(@Lazy KnowledgeService knowledgeService,
                          KnowledgeMapper knowledgeMapper,
                          KnowledgeTagMapper knowledgeTagMapper,
                          KnowledgeArtifactMapper knowledgeArtifactMapper,
                          KnowledgeRelationMapper knowledgeRelationMapper,
                          RetrievalEngine retrievalEngine) {
        this.knowledgeService = knowledgeService;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeTagMapper = knowledgeTagMapper;
        this.knowledgeArtifactMapper = knowledgeArtifactMapper;
        this.knowledgeRelationMapper = knowledgeRelationMapper;
        this.retrievalEngine = retrievalEngine;
    }

    /**
     * 为指定经验重建自动关系边：先清理旧 auto 边，再按规则与语义近邻重新建边
     */
    @Transactional(rollbackFor = Exception.class)
    public int buildRelations(Long knowledgeId, String workspaceId) {
        KnowledgeAggregate sourceAggregate = knowledgeService.getDetail(knowledgeId, workspaceId);
        if (sourceAggregate.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
            return 0;
        }

        deleteAutoRelationsBySource(knowledgeId, workspaceId);

        List<KnowledgeEntity> candidateEntityList = listPublishedCandidates(workspaceId, knowledgeId);
        if (candidateEntityList.isEmpty()) {
            return 0;
        }

        Map<Long, List<String>> workspaceTagMap = buildWorkspaceTagMap(workspaceId);
        Map<Long, List<KnowledgeArtifactEntity>> workspaceArtifactMap = buildWorkspaceArtifactMap(
                candidateEntityList, knowledgeId);

        int createdCount = 0;
        createdCount += buildSameModuleRelations(sourceAggregate, candidateEntityList, workspaceId);
        createdCount += buildSameTagRelations(sourceAggregate, candidateEntityList, workspaceTagMap, workspaceId);
        createdCount += buildSameArtifactRelations(
                sourceAggregate, candidateEntityList, workspaceArtifactMap, workspaceId);
        createdCount += buildRelatedDecisionRelations(sourceAggregate, candidateEntityList, workspaceId);
        createdCount += buildSemanticRelations(sourceAggregate, workspaceId, knowledgeId);

        log.info("RelationEngine 完成自动建边 knowledgeId={} workspaceId={} created={}",
                knowledgeId, workspaceId, createdCount);
        return createdCount;
    }

    /**
     * 删除指定经验作为 source 的全部自动边
     */
    public void deleteAutoRelationsBySource(Long sourceId, String workspaceId) {
        LambdaQueryWrapper<KnowledgeRelationEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        queryWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getSourceId, sourceId)
                .eq(KnowledgeRelationEntity::getRelationSource, RelationConstants.RELATION_SOURCE_AUTO);
        knowledgeRelationMapper.delete(queryWrapper);
    }

    /**
     * 经验下架时清理关系：仅删除自动边，保留 supersedes / depends_on 等人工治理边
     */
    public void cleanupRelationsOnDeprecate(Long knowledgeId, String workspaceId) {
        LambdaQueryWrapper<KnowledgeRelationEntity> sourceWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        sourceWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getSourceId, knowledgeId)
                .eq(KnowledgeRelationEntity::getRelationSource, RelationConstants.RELATION_SOURCE_AUTO);
        knowledgeRelationMapper.delete(sourceWrapper);

        LambdaQueryWrapper<KnowledgeRelationEntity> targetWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        targetWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getTargetId, knowledgeId)
                .eq(KnowledgeRelationEntity::getRelationSource, RelationConstants.RELATION_SOURCE_AUTO);
        knowledgeRelationMapper.delete(targetWrapper);
    }

    /**
     * 删除与指定经验相关的全部边（source 或 target）
     */
    public void deleteRelationsByKnowledgeId(Long knowledgeId, String workspaceId) {
        LambdaQueryWrapper<KnowledgeRelationEntity> sourceWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        sourceWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getSourceId, knowledgeId);
        knowledgeRelationMapper.delete(sourceWrapper);

        LambdaQueryWrapper<KnowledgeRelationEntity> targetWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        targetWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getTargetId, knowledgeId);
        knowledgeRelationMapper.delete(targetWrapper);
    }

    /**
     * 同 project + module 规则建边
     */
    private int buildSameModuleRelations(KnowledgeAggregate sourceAggregate,
                                         List<KnowledgeEntity> candidateEntityList,
                                         String workspaceId) {
        if (StringUtils.isAnyBlank(sourceAggregate.getProject(), sourceAggregate.getModule())) {
            return 0;
        }
        int count = 0;
        Map<String, Object> metadata = new HashMap<String, Object>();
        metadata.put("project", sourceAggregate.getProject());
        metadata.put("module", sourceAggregate.getModule());
        for (KnowledgeEntity candidateEntity : candidateEntityList) {
            if (StringUtils.equals(sourceAggregate.getProject(), candidateEntity.getProject())
                    && StringUtils.equals(sourceAggregate.getModule(), candidateEntity.getModule())) {
                if (saveAutoRelation(workspaceId, sourceAggregate.getId(), candidateEntity.getId(),
                        RelationConstants.TYPE_SAME_MODULE, 0.9D, metadata)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 标签交集规则建边，共有标签写入 metadata
     */
    private int buildSameTagRelations(KnowledgeAggregate sourceAggregate,
                                      List<KnowledgeEntity> candidateEntityList,
                                      Map<Long, List<String>> workspaceTagMap,
                                      String workspaceId) {
        List<String> sourceTags = workspaceTagMap.get(sourceAggregate.getId());
        if (sourceTags == null || sourceTags.isEmpty()) {
            return 0;
        }
        Set<String> sourceTagSet = normalizeTagSet(sourceTags);
        int count = 0;
        for (KnowledgeEntity candidateEntity : candidateEntityList) {
            List<String> candidateTags = workspaceTagMap.get(candidateEntity.getId());
            if (candidateTags == null || candidateTags.isEmpty()) {
                continue;
            }
            List<String> sharedTagList = findSharedTagNames(sourceTagSet, candidateTags);
            if (sharedTagList.isEmpty()) {
                continue;
            }
            double confidence = Math.min(0.95D, 0.75D + sharedTagList.size() * 0.05D);
            Map<String, Object> metadata = new HashMap<String, Object>();
            metadata.put("sharedTags", sharedTagList);
            if (saveAutoRelation(workspaceId, sourceAggregate.getId(), candidateEntity.getId(),
                    RelationConstants.TYPE_SAME_TAG, confidence, metadata)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 同源 Artifact 规则建边
     */
    private int buildSameArtifactRelations(KnowledgeAggregate sourceAggregate,
                                           List<KnowledgeEntity> candidateEntityList,
                                           Map<Long, List<KnowledgeArtifactEntity>> workspaceArtifactMap,
                                           String workspaceId) {
        List<KnowledgeArtifactEntity> sourceArtifactList = workspaceArtifactMap.get(sourceAggregate.getId());
        if (sourceArtifactList == null || sourceArtifactList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (KnowledgeEntity candidateEntity : candidateEntityList) {
            List<KnowledgeArtifactEntity> candidateArtifactList = workspaceArtifactMap.get(candidateEntity.getId());
            if (candidateArtifactList == null || candidateArtifactList.isEmpty()) {
                continue;
            }
            List<Map<String, String>> matchedArtifactList = findMatchedArtifacts(sourceArtifactList, candidateArtifactList);
            if (matchedArtifactList.isEmpty()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<String, Object>();
            metadata.put("matchedArtifacts", matchedArtifactList);
            if (saveAutoRelation(workspaceId, sourceAggregate.getId(), candidateEntity.getId(),
                    RelationConstants.TYPE_SAME_ARTIFACT, 0.95D, metadata)) {
                count++;
            }
        }
        return count;
    }

    /**
     * experience 与 decision 同模块时建立 related_decision 边
     */
    private int buildRelatedDecisionRelations(KnowledgeAggregate sourceAggregate,
                                              List<KnowledgeEntity> candidateEntityList,
                                              String workspaceId) {
        if (StringUtils.isBlank(sourceAggregate.getModule())) {
            return 0;
        }
        int count = 0;
        for (KnowledgeEntity candidateEntity : candidateEntityList) {
            if (!StringUtils.equals(sourceAggregate.getModule(), candidateEntity.getModule())) {
                continue;
            }
            boolean sourceIsDecision = MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(sourceAggregate.getKnowledgeType());
            boolean targetIsDecision = MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(candidateEntity.getKnowledgeType());
            if (sourceIsDecision == targetIsDecision) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<String, Object>();
            metadata.put("module", sourceAggregate.getModule());
            if (saveAutoRelation(workspaceId, sourceAggregate.getId(), candidateEntity.getId(),
                    RelationConstants.TYPE_RELATED_DECISION, 0.85D, metadata)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 语义相近建边：文本相似度 Top-K 且 similarity >= 0.85
     */
    private int buildSemanticRelations(KnowledgeAggregate sourceAggregate,
                                       String workspaceId,
                                       Long knowledgeId) {
        List<Long> candidateIdList = knowledgeService.listPublishedKnowledgeIds(
                workspaceId, listAllKnowledgeTypes());
        candidateIdList.remove(knowledgeId);
        if (candidateIdList.isEmpty()) {
            return 0;
        }
        Set<Long> allowedKnowledgeIds = retrievalEngine.toAllowedKnowledgeIdSet(candidateIdList);
        String queryText = buildIndexText(sourceAggregate);
        List<RetrievalEngine.RetrievalCandidate> candidateList = retrievalEngine.search(
                queryText, null, RelationConstants.SEMANTIC_EDGE_TOP_K, allowedKnowledgeIds);

        int count = 0;
        for (RetrievalEngine.RetrievalCandidate retrievalCandidate : candidateList) {
            if (retrievalCandidate.getSimilarityScore() < RelationConstants.SEMANTIC_EDGE_MIN_SIMILARITY) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<String, Object>();
            metadata.put("similarity", retrievalCandidate.getSimilarityScore());
            if (saveAutoRelation(workspaceId, knowledgeId, retrievalCandidate.getKnowledgeId(),
                    RelationConstants.TYPE_RELATED_SEMANTIC,
                    retrievalCandidate.getSimilarityScore(), metadata)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 经验重新启用后，补建其他已发布节点指向该节点的规则类自动边（下架时已清理双向 auto 边）
     */
    public int rebuildInboundAutoRelations(Long targetKnowledgeId, String workspaceId) {
        KnowledgeAggregate targetAggregate = knowledgeService.getDetail(targetKnowledgeId, workspaceId);
        if (targetAggregate.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
            return 0;
        }

        List<KnowledgeEntity> sourceEntityList = listPublishedCandidates(workspaceId, targetKnowledgeId);
        if (sourceEntityList.isEmpty()) {
            return 0;
        }

        KnowledgeEntity targetEntity = knowledgeMapper.selectById(targetKnowledgeId);
        if (targetEntity == null) {
            return 0;
        }
        List<KnowledgeEntity> targetAsSingleCandidate = Collections.singletonList(targetEntity);
        Map<Long, List<String>> workspaceTagMap = buildWorkspaceTagMap(workspaceId);
        Map<Long, List<KnowledgeArtifactEntity>> workspaceArtifactMap = buildWorkspaceArtifactMap(
                sourceEntityList, targetKnowledgeId);

        int createdCount = 0;
        for (KnowledgeEntity sourceEntity : sourceEntityList) {
            KnowledgeAggregate sourceAggregate = knowledgeService.getDetail(sourceEntity.getId(), workspaceId);
            createdCount += buildSameModuleRelations(sourceAggregate, targetAsSingleCandidate, workspaceId);
            createdCount += buildSameTagRelations(sourceAggregate, targetAsSingleCandidate, workspaceTagMap, workspaceId);
            createdCount += buildSameArtifactRelations(
                    sourceAggregate, targetAsSingleCandidate, workspaceArtifactMap, workspaceId);
            createdCount += buildRelatedDecisionRelations(sourceAggregate, targetAsSingleCandidate, workspaceId);
        }

        log.info("RelationEngine 完成入向自动边补建 targetKnowledgeId={} workspaceId={} created={}",
                targetKnowledgeId, workspaceId, createdCount);
        return createdCount;
    }

    /**
     * 写入自动关系边，命中唯一键时更新置信度与 metadata；不覆盖人工或合并导入边
     */
    private boolean saveAutoRelation(String workspaceId,
                                     Long sourceId,
                                     Long targetId,
                                     String relationType,
                                     double confidence,
                                     Map<String, Object> metadata) {
        if (sourceId.equals(targetId)) {
            return false;
        }
        LambdaQueryWrapper<KnowledgeRelationEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeRelationEntity>();
        queryWrapper.eq(KnowledgeRelationEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeRelationEntity::getSourceId, sourceId)
                .eq(KnowledgeRelationEntity::getTargetId, targetId)
                .eq(KnowledgeRelationEntity::getRelationType, relationType);
        KnowledgeRelationEntity existingEntity = knowledgeRelationMapper.selectOne(queryWrapper);

        if (existingEntity != null && isProtectedRelationSource(existingEntity.getRelationSource())) {
            return false;
        }

        KnowledgeRelationEntity relationEntity = existingEntity == null
                ? new KnowledgeRelationEntity() : existingEntity;
        relationEntity.setWorkspaceId(workspaceId);
        relationEntity.setSourceId(sourceId);
        relationEntity.setTargetId(targetId);
        relationEntity.setRelationType(relationType);
        relationEntity.setRelationSource(RelationConstants.RELATION_SOURCE_AUTO);
        relationEntity.setConfidence(toDecimal(confidence));
        relationEntity.setRelationMetadata(MemoryJsonUtil.toJson(metadata));

        if (existingEntity == null) {
            knowledgeRelationMapper.insert(relationEntity);
            return true;
        }
        knowledgeRelationMapper.updateById(relationEntity);
        return false;
    }

    /**
     * 人工边与合并导入边不可被自动建边覆盖
     */
    private boolean isProtectedRelationSource(String relationSource) {
        return RelationConstants.RELATION_SOURCE_MANUAL.equals(relationSource)
                || RelationConstants.RELATION_SOURCE_MERGE.equals(relationSource);
    }

    private List<KnowledgeEntity> listPublishedCandidates(String workspaceId, Long excludeKnowledgeId) {
        LambdaQueryWrapper<KnowledgeEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        queryWrapper.eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED)
                .ne(KnowledgeEntity::getId, excludeKnowledgeId);
        return knowledgeMapper.selectList(queryWrapper);
    }

    private Map<Long, List<String>> buildWorkspaceTagMap(String workspaceId) {
        List<KnowledgeTagRow> tagRowList = knowledgeTagMapper.selectTagRowsByWorkspace(workspaceId);
        Map<Long, List<String>> tagMap = new HashMap<Long, List<String>>();
        for (KnowledgeTagRow tagRow : tagRowList) {
            if (tagRow.getKnowledgeId() == null || StringUtils.isBlank(tagRow.getTagName())) {
                continue;
            }
            List<String> tagList = tagMap.get(tagRow.getKnowledgeId());
            if (tagList == null) {
                tagList = new ArrayList<String>();
                tagMap.put(tagRow.getKnowledgeId(), tagList);
            }
            tagList.add(tagRow.getTagName());
        }
        return tagMap;
    }

    private Map<Long, List<KnowledgeArtifactEntity>> buildWorkspaceArtifactMap(
            List<KnowledgeEntity> candidateEntityList, Long sourceKnowledgeId) {
        List<Long> knowledgeIdList = new ArrayList<Long>();
        knowledgeIdList.add(sourceKnowledgeId);
        for (KnowledgeEntity candidateEntity : candidateEntityList) {
            knowledgeIdList.add(candidateEntity.getId());
        }
        LambdaQueryWrapper<KnowledgeArtifactEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeArtifactEntity>();
        queryWrapper.in(KnowledgeArtifactEntity::getKnowledgeId, knowledgeIdList);
        List<KnowledgeArtifactEntity> artifactEntityList = knowledgeArtifactMapper.selectList(queryWrapper);

        Map<Long, List<KnowledgeArtifactEntity>> artifactMap = new HashMap<Long, List<KnowledgeArtifactEntity>>();
        for (KnowledgeArtifactEntity artifactEntity : artifactEntityList) {
            List<KnowledgeArtifactEntity> bucket = artifactMap.get(artifactEntity.getKnowledgeId());
            if (bucket == null) {
                bucket = new ArrayList<KnowledgeArtifactEntity>();
                artifactMap.put(artifactEntity.getKnowledgeId(), bucket);
            }
            bucket.add(artifactEntity);
        }
        return artifactMap;
    }

    private List<Map<String, String>> findMatchedArtifacts(List<KnowledgeArtifactEntity> sourceArtifactList,
                                                           List<KnowledgeArtifactEntity> candidateArtifactList) {
        List<Map<String, String>> matchedList = new ArrayList<Map<String, String>>();
        for (KnowledgeArtifactEntity sourceArtifact : sourceArtifactList) {
            for (KnowledgeArtifactEntity candidateArtifact : candidateArtifactList) {
                if (isSameArtifact(sourceArtifact, candidateArtifact)) {
                    Map<String, String> matched = new HashMap<String, String>();
                    matched.put("artifactType", sourceArtifact.getArtifactType());
                    if (StringUtils.isNotBlank(sourceArtifact.getArtifactUrl())) {
                        matched.put("artifactUrl", sourceArtifact.getArtifactUrl());
                    }
                    if (StringUtils.isNotBlank(sourceArtifact.getContentRef())) {
                        matched.put("contentRef", sourceArtifact.getContentRef());
                    }
                    matchedList.add(matched);
                }
            }
        }
        return matchedList;
    }

    private boolean isSameArtifact(KnowledgeArtifactEntity left, KnowledgeArtifactEntity right) {
        if (StringUtils.isNotBlank(left.getArtifactUrl()) && StringUtils.isNotBlank(right.getArtifactUrl())
                && StringUtils.equals(left.getArtifactUrl(), right.getArtifactUrl())) {
            return true;
        }
        return StringUtils.isNotBlank(left.getContentRef()) && StringUtils.isNotBlank(right.getContentRef())
                && StringUtils.equals(left.getContentRef(), right.getContentRef());
    }

    private Set<String> normalizeTagSet(List<String> tagList) {
        Set<String> tagSet = new HashSet<String>();
        for (String tagName : tagList) {
            if (StringUtils.isNotBlank(tagName)) {
                tagSet.add(tagName.trim().toLowerCase());
            }
        }
        return tagSet;
    }

    private List<String> findSharedTagNames(Set<String> sourceTagSet, List<String> candidateTags) {
        List<String> sharedTagList = new ArrayList<String>();
        for (String tagName : candidateTags) {
            if (StringUtils.isNotBlank(tagName) && sourceTagSet.contains(tagName.trim().toLowerCase())) {
                sharedTagList.add(tagName.trim());
            }
        }
        return sharedTagList;
    }

    private String buildIndexText(KnowledgeAggregate aggregate) {
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.defaultString(aggregate.getTitle())).append(" ");
        builder.append(StringUtils.defaultString(aggregate.getProject())).append(" ");
        builder.append(StringUtils.defaultString(aggregate.getModule())).append(" ");
        builder.append(StringUtils.defaultString(aggregate.getRepository())).append(" ");
        if (aggregate.getTags() != null) {
            builder.append(StringUtils.join(aggregate.getTags(), " ")).append(" ");
        }
        if (aggregate.getFacts() != null) {
            for (FactBlock factBlock : aggregate.getFacts()) {
                if (factBlock != null && StringUtils.isNotBlank(factBlock.getText())) {
                    builder.append(factBlock.getText()).append(" ");
                }
            }
        }
        return builder.toString().trim();
    }

    private List<String> listAllKnowledgeTypes() {
        return Arrays.asList(
                MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE,
                MemoryConstants.KNOWLEDGE_TYPE_RULE,
                MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW,
                MemoryConstants.KNOWLEDGE_TYPE_DECISION);
    }

    private BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
