package com.zhiyi.memory.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.context.ContextQueryBuilder;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.domain.RecallExecutionMeta;
import com.zhiyi.memory.domain.RecallResponse;
import com.zhiyi.memory.domain.RecallResult;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.retrieval.GraphRetrievalClient;
import com.zhiyi.memory.retrieval.RetrievalEngine;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Recall 引擎：RecallContext → 召回并按 Task Match 排序的 Fact Blocks，按工作空间隔离
 */
@Service
public class RecallEngine {

    /** 图谱扩展时最多取前 N 个向量种子 */
    private static final int GRAPH_SEED_LIMIT = 5;

    /** 图谱扩展新增节点的默认相似度基线 */
    private static final double GRAPH_ONLY_SIMILARITY_BASE = 0.4D;

    private final ContextNormalizer contextNormalizer;
    private final ContextQueryBuilder contextQueryBuilder;
    private final RetrievalEngine retrievalEngine;
    private final GraphRetrievalClient graphRetrievalClient;
    private final RankingEngine rankingEngine;
    private final KnowledgeService knowledgeService;
    private final KnowledgeArtifactMapper knowledgeArtifactMapper;

    public RecallEngine(ContextNormalizer contextNormalizer,
                        ContextQueryBuilder contextQueryBuilder,
                        RetrievalEngine retrievalEngine,
                        GraphRetrievalClient graphRetrievalClient,
                        RankingEngine rankingEngine,
                        KnowledgeService knowledgeService,
                        KnowledgeArtifactMapper knowledgeArtifactMapper) {
        this.contextNormalizer = contextNormalizer;
        this.contextQueryBuilder = contextQueryBuilder;
        this.retrievalEngine = retrievalEngine;
        this.graphRetrievalClient = graphRetrievalClient;
        this.rankingEngine = rankingEngine;
        this.knowledgeService = knowledgeService;
        this.knowledgeArtifactMapper = knowledgeArtifactMapper;
    }

    /**
     * 执行 Recall，返回 Agent 响应与质量分析用执行元信息
     */
    public RecallResult recall(RecallContext recallContext) {
        RecallContext normalizedContext = contextNormalizer.normalize(recallContext);
        String workspaceId = requireWorkspaceId(normalizedContext.getWorkspaceId());
        String queryText = contextQueryBuilder.buildQueryText(normalizedContext);
        int limit = normalizedContext.getLimit() == null ? 10 : normalizedContext.getLimit();
        Set<Long> allowedKnowledgeIds = retrievalEngine.toAllowedKnowledgeIdSet(
                knowledgeService.listPublishedKnowledgeIds(workspaceId, normalizedContext.getKnowledgeTypes()));

        List<RetrievalEngine.RetrievalCandidate> candidates =
                retrievalEngine.search(queryText, normalizedContext, limit * 3, allowedKnowledgeIds);
        int retrievalCandidateCount = candidates.size();
        boolean fallbackUsed = false;
        if (candidates.isEmpty()) {
            candidates = buildFallbackCandidates(limit * 3, allowedKnowledgeIds);
            fallbackUsed = true;
        }

        boolean graphExpandUsed = Boolean.TRUE.equals(normalizedContext.getExpandGraph());
        Map<Long, Integer> hopDistanceMap = Collections.emptyMap();
        int graphExpandedCandidateCount = 0;
        if (graphExpandUsed) {
            int graphDepth = normalizedContext.getGraphDepth() == null ? 2 : normalizedContext.getGraphDepth();
            List<String> relationTypeList = graphRetrievalClient.resolveRecallRelationTypes(
                    normalizedContext.getGraphRelationTypes());
            Set<Long> seedIdSet = collectGraphSeedIds(candidates);
            hopDistanceMap = graphRetrievalClient.expandFromSeeds(
                    seedIdSet, workspaceId, graphDepth, relationTypeList);
            graphExpandedCandidateCount = mergeGraphCandidates(
                    candidates, hopDistanceMap, allowedKnowledgeIds);
        }

        List<ScoredKnowledge> scoredList = new ArrayList<ScoredKnowledge>();
        for (RetrievalEngine.RetrievalCandidate candidate : candidates) {
            KnowledgeAggregate aggregate = knowledgeService.getDetail(candidate.getKnowledgeId(), workspaceId);
            if (aggregate.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
                continue;
            }
            List<KnowledgeArtifactEntity> artifactEntities = loadArtifactEntities(candidate.getKnowledgeId());
            double graphProximity = resolveGraphProximity(candidate.getKnowledgeId(), graphExpandUsed, hopDistanceMap);
            RankingEngine.RankingScore rankingScore = rankingEngine.rank(
                    aggregate, normalizedContext, candidate.getSimilarityScore(), artifactEntities, graphProximity);
            ScoredKnowledge scoredKnowledge = new ScoredKnowledge();
            scoredKnowledge.setAggregate(aggregate);
            scoredKnowledge.setScore(rankingScore.getFinalScore());
            scoredKnowledge.setBreakdown(rankingScore.getBreakdown());
            scoredList.add(scoredKnowledge);
        }

        Collections.sort(scoredList, new Comparator<ScoredKnowledge>() {
            @Override
            public int compare(ScoredKnowledge left, ScoredKnowledge right) {
                return Double.compare(right.getScore(), left.getScore());
            }
        });

        RecallResponse recallResponse = new RecallResponse();
        recallResponse.setSessionId("rec_" + UUID.randomUUID().toString().replace("-", ""));
        recallResponse.setItems(buildRecallItems(scoredList, normalizedContext, limit));
        recallResponse.setPromptBlock(buildPromptBlock(recallResponse.getItems(), normalizedContext));
        for (RecallResponse.RecallItem item : recallResponse.getItems()) {
            knowledgeService.increaseRecallCount(item.getKnowledgeId());
        }

        RecallExecutionMeta executionMeta = new RecallExecutionMeta();
        executionMeta.setQueryText(queryText);
        executionMeta.setFallbackUsed(fallbackUsed);
        executionMeta.setRetrievalCandidateCount(retrievalCandidateCount);
        executionMeta.setRankedCandidateCount(scoredList.size());
        executionMeta.setGraphExpandUsed(graphExpandUsed);
        executionMeta.setGraphExpandedCandidateCount(graphExpandedCandidateCount);

        RecallResult recallResult = new RecallResult();
        recallResult.setResponse(recallResponse);
        recallResult.setExecutionMeta(executionMeta);
        return recallResult;
    }

    /**
     * 解析 Recall 工作空间主键
     */
    private String requireWorkspaceId(String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "Recall 缺少工作空间上下文");
        }
        return workspaceId;
    }

    private List<RetrievalEngine.RetrievalCandidate> buildFallbackCandidates(int topK, Set<Long> allowedKnowledgeIds) {
        List<RetrievalEngine.RetrievalCandidate> candidates = new ArrayList<RetrievalEngine.RetrievalCandidate>();
        for (Long knowledgeId : allowedKnowledgeIds) {
            RetrievalEngine.RetrievalCandidate candidate = new RetrievalEngine.RetrievalCandidate();
            candidate.setKnowledgeId(knowledgeId);
            candidate.setSimilarityScore(0.1D);
            candidates.add(candidate);
            if (candidates.size() >= topK) {
                break;
            }
        }
        return candidates;
    }

    private List<KnowledgeArtifactEntity> loadArtifactEntities(Long knowledgeId) {
        LambdaQueryWrapper<KnowledgeArtifactEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeArtifactEntity>();
        queryWrapper.eq(KnowledgeArtifactEntity::getKnowledgeId, knowledgeId);
        return knowledgeArtifactMapper.selectList(queryWrapper);
    }

    /**
     * 从向量检索结果中选取图谱扩展种子节点
     */
    private Set<Long> collectGraphSeedIds(List<RetrievalEngine.RetrievalCandidate> candidateList) {
        Set<Long> seedIdSet = new HashSet<Long>();
        int seedLimit = Math.min(GRAPH_SEED_LIMIT, candidateList.size());
        for (int index = 0; index < seedLimit; index++) {
            seedIdSet.add(candidateList.get(index).getKnowledgeId());
        }
        return seedIdSet;
    }

    /**
     * 将图谱扩展节点并入候选集，返回新增数量
     */
    private int mergeGraphCandidates(List<RetrievalEngine.RetrievalCandidate> candidateList,
                                     Map<Long, Integer> hopDistanceMap,
                                     Set<Long> allowedKnowledgeIds) {
        Set<Long> existingIdSet = new HashSet<Long>();
        for (RetrievalEngine.RetrievalCandidate candidate : candidateList) {
            existingIdSet.add(candidate.getKnowledgeId());
        }
        int addedCount = 0;
        for (Map.Entry<Long, Integer> entry : hopDistanceMap.entrySet()) {
            Long knowledgeId = entry.getKey();
            if (existingIdSet.contains(knowledgeId) || !allowedKnowledgeIds.contains(knowledgeId)) {
                continue;
            }
            RetrievalEngine.RetrievalCandidate graphCandidate = new RetrievalEngine.RetrievalCandidate();
            graphCandidate.setKnowledgeId(knowledgeId);
            graphCandidate.setSimilarityScore(GRAPH_ONLY_SIMILARITY_BASE);
            candidateList.add(graphCandidate);
            existingIdSet.add(knowledgeId);
            addedCount++;
        }
        return addedCount;
    }

    /**
     * 根据节点在图谱中的跳数计算 graphProximity；未扩展或未命中时返回 1
     */
    private double resolveGraphProximity(Long knowledgeId,
                                         boolean graphExpandUsed,
                                         Map<Long, Integer> hopDistanceMap) {
        if (!graphExpandUsed || hopDistanceMap == null || hopDistanceMap.isEmpty()) {
            return 1.0D;
        }
        Integer hopDistance = hopDistanceMap.get(knowledgeId);
        if (hopDistance == null) {
            return 1.0D;
        }
        return graphRetrievalClient.calculateGraphProximity(hopDistance.intValue());
    }

    private List<RecallResponse.RecallItem> buildRecallItems(List<ScoredKnowledge> scoredList,
                                                              RecallContext recallContext,
                                                              int limit) {
        List<RecallResponse.RecallItem> itemList = new ArrayList<RecallResponse.RecallItem>();
        int count = 0;
        for (ScoredKnowledge scoredKnowledge : scoredList) {
            if (count >= limit) {
                break;
            }
            KnowledgeAggregate aggregate = scoredKnowledge.getAggregate();
            RecallResponse.RecallItem item = new RecallResponse.RecallItem();
            item.setKnowledgeId(aggregate.getId());
            item.setTitle(aggregate.getTitle());
            item.setKnowledgeType(aggregate.getKnowledgeType());
            item.setFacts(filterFacts(aggregate.getFacts(), recallContext.getFactTypes(),
                    recallContext.getKnowledgeTypes()));
            item.setArtifacts(aggregate.getArtifacts());
            item.setScore(scoredKnowledge.getScore());
            item.setScoreBreakdown(scoredKnowledge.getBreakdown());
            itemList.add(item);
            count++;
        }
        return itemList;
    }

    /**
     * 按 factTypes 过滤 Fact Block；传 knowledgeTypes 时默认含 rule
     */
    private List<FactBlock> filterFacts(List<FactBlock> factBlocks, List<String> factTypes,
                                        List<String> knowledgeTypes) {
        if (factBlocks == null) {
            return Collections.emptyList();
        }
        List<String> targetTypes = factTypes;
        if (targetTypes == null || targetTypes.isEmpty()) {
            targetTypes = new ArrayList<String>();
            if (knowledgeTypes != null && !knowledgeTypes.isEmpty()) {
                targetTypes.add("rule");
                targetTypes.add("constraint");
                targetTypes.add("decision");
                targetTypes.add("action");
            } else {
                targetTypes.add("decision");
                targetTypes.add("constraint");
                targetTypes.add("action");
            }
        }
        List<FactBlock> filtered = new ArrayList<FactBlock>();
        for (FactBlock factBlock : factBlocks) {
            if (targetTypes.contains(factBlock.getType())) {
                filtered.add(factBlock);
            }
        }
        if (filtered.isEmpty()) {
            return factBlocks;
        }
        return filtered;
    }

    /**
     * 将 Recall 结果按知识类型分组组装 prompt：规则 → 流程 → 决策 → 经验
     */
    private String buildPromptBlock(List<RecallResponse.RecallItem> items, RecallContext recallContext) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        boolean hasKnowledgeTypeFilter = recallContext.getKnowledgeTypes() != null
                && !recallContext.getKnowledgeTypes().isEmpty();
        if (!hasKnowledgeTypeFilter) {
            return buildLegacyPromptBlock(items);
        }

        StringBuilder ruleSection = new StringBuilder();
        StringBuilder workflowSection = new StringBuilder();
        StringBuilder decisionSection = new StringBuilder();
        StringBuilder experienceSection = new StringBuilder();

        for (RecallResponse.RecallItem item : items) {
            String knowledgeType = resolveKnowledgeType(item);
            StringBuilder section = experienceSection;
            if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(knowledgeType)) {
                section = ruleSection;
            } else if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(knowledgeType)) {
                section = workflowSection;
            } else if (MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(knowledgeType)) {
                section = decisionSection;
            }
            appendRecallItem(section, item);
        }

        StringBuilder builder = new StringBuilder();
        if (ruleSection.length() > 0) {
            builder.append("## 规则\n").append(ruleSection);
        }
        if (workflowSection.length() > 0) {
            builder.append("## 流程\n").append(workflowSection);
        }
        if (decisionSection.length() > 0) {
            builder.append("## 决策\n").append(decisionSection);
        }
        if (experienceSection.length() > 0) {
            builder.append("## 经验\n").append(experienceSection);
        }
        return builder.toString().trim();
    }

    private String buildLegacyPromptBlock(List<RecallResponse.RecallItem> items) {
        StringBuilder builder = new StringBuilder();
        for (RecallResponse.RecallItem item : items) {
            appendRecallItem(builder, item);
        }
        return builder.toString().trim();
    }

    private void appendRecallItem(StringBuilder builder, RecallResponse.RecallItem item) {
        builder.append("### ").append(item.getTitle()).append("\n");
        if (item.getFacts() != null) {
            for (FactBlock factBlock : item.getFacts()) {
                builder.append("- [").append(factBlock.getType()).append("] ")
                        .append(factBlock.getText()).append("\n");
            }
        }
        builder.append("\n");
    }

    private String resolveKnowledgeType(RecallResponse.RecallItem item) {
        if (item.getKnowledgeType() != null) {
            return item.getKnowledgeType();
        }
        return MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE;
    }

    private static class ScoredKnowledge {

        private KnowledgeAggregate aggregate;

        private double score;

        private Map<String, Double> breakdown = new HashMap<String, Double>();

        public KnowledgeAggregate getAggregate() {
            return aggregate;
        }

        public void setAggregate(KnowledgeAggregate aggregate) {
            this.aggregate = aggregate;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public Map<String, Double> getBreakdown() {
            return breakdown;
        }

        public void setBreakdown(Map<String, Double> breakdown) {
            this.breakdown = breakdown;
        }
    }
}
