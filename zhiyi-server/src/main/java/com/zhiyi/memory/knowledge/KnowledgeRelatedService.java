package com.zhiyi.memory.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.KnowledgeRelatedItemVO;
import com.zhiyi.memory.knowledge.KnowledgeRelationService.ExplicitRelationSummary;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.engine.RankingEngine;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import com.zhiyi.memory.retrieval.RetrievalEngine;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 相关经验服务：基于 Task Match、文本相似度与同标签加权，推荐同工作空间内的关联经验
 */
@Service
public class KnowledgeRelatedService {

    /** 同标签加权系数，用于在 Ranking 分数上叠加标签交集贡献 */
    private static final double TAG_OVERLAP_BOOST_WEIGHT = 0.15D;

    /** 向量检索候选倍数，先多召回再精排 */
    private static final int CANDIDATE_MULTIPLIER = 3;

    /** 无向量命中时的兜底相似度 */
    private static final double FALLBACK_SIMILARITY_SCORE = 0.1D;

    /** 显式关系边对隐式分数的加权系数 */
    private static final double EXPLICIT_RELATION_BOOST = 1.35D;

    private final KnowledgeService knowledgeService;
    private final KnowledgeRelationService knowledgeRelationService;
    private final RetrievalEngine retrievalEngine;
    private final RankingEngine rankingEngine;
    private final KnowledgeArtifactMapper knowledgeArtifactMapper;

    public KnowledgeRelatedService(KnowledgeService knowledgeService,
                                   KnowledgeRelationService knowledgeRelationService,
                                   RetrievalEngine retrievalEngine,
                                   RankingEngine rankingEngine,
                                   KnowledgeArtifactMapper knowledgeArtifactMapper) {
        this.knowledgeService = knowledgeService;
        this.knowledgeRelationService = knowledgeRelationService;
        this.retrievalEngine = retrievalEngine;
        this.rankingEngine = rankingEngine;
        this.knowledgeArtifactMapper = knowledgeArtifactMapper;
    }

    /**
     * 查询与指定经验相关的已发布经验列表，按综合相关度降序
     */
    public List<KnowledgeRelatedItemVO> listRelated(Long knowledgeId, String workspaceId, int limit) {
        int safeLimit = normalizeLimit(limit);
        KnowledgeAggregate sourceAggregate = knowledgeService.getDetail(knowledgeId, workspaceId);
        Map<Long, ExplicitRelationSummary> explicitRelationMap = knowledgeRelationService.summarizeExplicitRelations(
                knowledgeId, workspaceId);

        List<ScoredRelatedKnowledge> scoredList = buildImplicitScoredList(sourceAggregate, workspaceId, knowledgeId);
        mergeExplicitRelations(scoredList, explicitRelationMap, workspaceId, sourceAggregate.getTags());

        Collections.sort(scoredList, new Comparator<ScoredRelatedKnowledge>() {
            @Override
            public int compare(ScoredRelatedKnowledge left, ScoredRelatedKnowledge right) {
                return Double.compare(right.getScore(), left.getScore());
            }
        });

        List<KnowledgeRelatedItemVO> resultList = new ArrayList<KnowledgeRelatedItemVO>();
        int count = 0;
        for (ScoredRelatedKnowledge scoredRelatedKnowledge : scoredList) {
            if (count >= safeLimit) {
                break;
            }
            resultList.add(toRelatedItemView(scoredRelatedKnowledge));
            count++;
        }
        return resultList;
    }

    /**
     * 基于 Task Match、文本相似度与同标签加权构建隐式相关候选
     */
    private List<ScoredRelatedKnowledge> buildImplicitScoredList(KnowledgeAggregate sourceAggregate,
                                                                 String workspaceId,
                                                                 Long knowledgeId) {
        List<Long> candidateIdList = knowledgeService.listPublishedKnowledgeIds(workspaceId);
        removeKnowledgeId(candidateIdList, knowledgeId);
        if (candidateIdList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> allowedKnowledgeIds = retrievalEngine.toAllowedKnowledgeIdSet(candidateIdList);
        RecallContext recallContext = buildRecallContextFromSource(sourceAggregate, workspaceId);
        String queryText = buildQueryTextFromSource(sourceAggregate);

        int candidateLimit = Math.min(30, allowedKnowledgeIds.size());
        List<RetrievalEngine.RetrievalCandidate> retrievalCandidates = retrievalEngine.search(
                queryText, recallContext, candidateLimit, allowedKnowledgeIds);
        if (retrievalCandidates.isEmpty()) {
            retrievalCandidates = buildFallbackCandidates(candidateLimit, allowedKnowledgeIds);
        }

        List<ScoredRelatedKnowledge> scoredList = new ArrayList<ScoredRelatedKnowledge>();
        for (RetrievalEngine.RetrievalCandidate retrievalCandidate : retrievalCandidates) {
            KnowledgeAggregate candidateAggregate = knowledgeService.getDetail(
                    retrievalCandidate.getKnowledgeId(), workspaceId);
            if (candidateAggregate.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
                continue;
            }
            List<KnowledgeArtifactEntity> artifactEntityList = loadArtifactEntities(
                    retrievalCandidate.getKnowledgeId());
            RankingEngine.RankingScore rankingScore = rankingEngine.rank(
                    candidateAggregate,
                    recallContext,
                    retrievalCandidate.getSimilarityScore(),
                    artifactEntityList);

            double tagOverlapScore = calculateTagOverlapScore(
                    sourceAggregate.getTags(), candidateAggregate.getTags());
            double relatedScore = rankingScore.getFinalScore() * (1D + tagOverlapScore * TAG_OVERLAP_BOOST_WEIGHT);

            Map<String, Double> scoreBreakdown = new HashMap<String, Double>(rankingScore.getBreakdown());
            scoreBreakdown.put("tagOverlap", tagOverlapScore);
            scoreBreakdown.put("relatedScore", relatedScore);

            ScoredRelatedKnowledge scoredRelatedKnowledge = new ScoredRelatedKnowledge();
            scoredRelatedKnowledge.setAggregate(candidateAggregate);
            scoredRelatedKnowledge.setScore(relatedScore);
            scoredRelatedKnowledge.setScoreBreakdown(scoreBreakdown);
            scoredRelatedKnowledge.setSharedTags(findSharedTags(sourceAggregate.getTags(), candidateAggregate.getTags()));
            scoredList.add(scoredRelatedKnowledge);
        }
        return scoredList;
    }

    /**
     * 合并显式关系边：提升已有候选分数，并补充仅存在图谱边的相关经验
     */
    private void mergeExplicitRelations(List<ScoredRelatedKnowledge> scoredList,
                                        Map<Long, ExplicitRelationSummary> explicitRelationMap,
                                        String workspaceId,
                                        List<String> sourceTags) {
        if (explicitRelationMap == null || explicitRelationMap.isEmpty()) {
            for (ScoredRelatedKnowledge scoredRelatedKnowledge : scoredList) {
                scoredRelatedKnowledge.setMatchSource("implicit");
            }
            return;
        }

        Map<Long, ScoredRelatedKnowledge> scoredById = new HashMap<Long, ScoredRelatedKnowledge>();
        for (ScoredRelatedKnowledge scoredRelatedKnowledge : scoredList) {
            scoredById.put(scoredRelatedKnowledge.getAggregate().getId(), scoredRelatedKnowledge);
        }

        for (Map.Entry<Long, ExplicitRelationSummary> entry : explicitRelationMap.entrySet()) {
            Long partnerId = entry.getKey();
            ExplicitRelationSummary explicitSummary = entry.getValue();
            ScoredRelatedKnowledge scoredRelatedKnowledge = scoredById.get(partnerId);
            if (scoredRelatedKnowledge != null) {
                double boostedScore = scoredRelatedKnowledge.getScore() * EXPLICIT_RELATION_BOOST;
                scoredRelatedKnowledge.setScore(boostedScore);
                scoredRelatedKnowledge.getScoreBreakdown().put("relatedScore", roundScore(boostedScore));
                scoredRelatedKnowledge.getScoreBreakdown().put("explicitBoost", EXPLICIT_RELATION_BOOST);
                scoredRelatedKnowledge.setRelationTypes(new ArrayList<String>(explicitSummary.getRelationTypes()));
                scoredRelatedKnowledge.setMatchSource(explicitSummary.isHasManualRelation() ? "mixed" : "both");
                continue;
            }

            KnowledgeAggregate partnerAggregate = knowledgeService.getDetail(partnerId, workspaceId);
            double explicitScore = explicitSummary.getMaxConfidence() * 0.5D + 0.25D;
            if (explicitSummary.isHasManualRelation()) {
                explicitScore += 0.2D;
            }
            Map<String, Double> scoreBreakdown = new HashMap<String, Double>();
            scoreBreakdown.put("explicitConfidence", explicitSummary.getMaxConfidence());
            scoreBreakdown.put("relatedScore", roundScore(explicitScore));

            ScoredRelatedKnowledge explicitRelated = new ScoredRelatedKnowledge();
            explicitRelated.setAggregate(partnerAggregate);
            explicitRelated.setScore(explicitScore);
            explicitRelated.setScoreBreakdown(scoreBreakdown);
            explicitRelated.setSharedTags(findSharedTags(sourceTags, partnerAggregate.getTags()));
            explicitRelated.setRelationTypes(new ArrayList<String>(explicitSummary.getRelationTypes()));
            explicitRelated.setMatchSource("explicit");
            scoredList.add(explicitRelated);
        }

        for (ScoredRelatedKnowledge scoredRelatedKnowledge : scoredList) {
            if (StringUtils.isBlank(scoredRelatedKnowledge.getMatchSource())) {
                scoredRelatedKnowledge.setMatchSource("implicit");
            }
        }
    }

    /**
     * 从源经验构造 Recall 上下文，供 Task Match 打分使用
     */
    private RecallContext buildRecallContextFromSource(KnowledgeAggregate sourceAggregate, String workspaceId) {
        RecallContext recallContext = new RecallContext();
        recallContext.setWorkspaceId(workspaceId);
        recallContext.setProject(sourceAggregate.getProject());
        recallContext.setModule(sourceAggregate.getModule());
        recallContext.setRepository(sourceAggregate.getRepository());
        recallContext.setLanguage(sourceAggregate.getLanguage());
        recallContext.setFramework(sourceAggregate.getFramework());
        recallContext.setTags(sourceAggregate.getTags());
        recallContext.setTask(sourceAggregate.getTitle());
        return recallContext;
    }

    /**
     * 拼接源经验的标题、上下文与 Fact 文本，作为向量/文本检索 Query
     */
    private String buildQueryTextFromSource(KnowledgeAggregate sourceAggregate) {
        StringBuilder builder = new StringBuilder();
        builder.append(StringUtils.defaultString(sourceAggregate.getTitle())).append(" ");
        builder.append(StringUtils.defaultString(sourceAggregate.getProject())).append(" ");
        builder.append(StringUtils.defaultString(sourceAggregate.getModule())).append(" ");
        builder.append(StringUtils.defaultString(sourceAggregate.getRepository())).append(" ");
        if (sourceAggregate.getTags() != null) {
            builder.append(StringUtils.join(sourceAggregate.getTags(), " ")).append(" ");
        }
        if (sourceAggregate.getFacts() != null) {
            for (FactBlock factBlock : sourceAggregate.getFacts()) {
                if (factBlock != null && StringUtils.isNotBlank(factBlock.getText())) {
                    builder.append(factBlock.getText()).append(" ");
                }
            }
        }
        return builder.toString().trim();
    }

    /**
     * 计算候选与源经验标签交集占比，范围 0~1
     */
    private double calculateTagOverlapScore(List<String> sourceTags, List<String> candidateTags) {
        if (sourceTags == null || sourceTags.isEmpty() || candidateTags == null || candidateTags.isEmpty()) {
            return 0D;
        }
        Set<String> sourceTagSet = new HashSet<String>();
        for (String tagName : sourceTags) {
            if (StringUtils.isNotBlank(tagName)) {
                sourceTagSet.add(tagName.trim().toLowerCase());
            }
        }
        if (sourceTagSet.isEmpty()) {
            return 0D;
        }
        int overlapCount = 0;
        for (String tagName : candidateTags) {
            if (StringUtils.isNotBlank(tagName) && sourceTagSet.contains(tagName.trim().toLowerCase())) {
                overlapCount++;
            }
        }
        if (overlapCount == 0) {
            return 0D;
        }
        return Math.min(1D, (double) overlapCount / (double) sourceTagSet.size());
    }

    /**
     * 提取源经验与候选经验的共有标签，用于前端高亮展示
     */
    private List<String> findSharedTags(List<String> sourceTags, List<String> candidateTags) {
        if (sourceTags == null || sourceTags.isEmpty() || candidateTags == null || candidateTags.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> sourceTagSet = new HashSet<String>();
        for (String tagName : sourceTags) {
            if (StringUtils.isNotBlank(tagName)) {
                sourceTagSet.add(tagName.trim().toLowerCase());
            }
        }
        List<String> sharedTagList = new ArrayList<String>();
        for (String tagName : candidateTags) {
            if (StringUtils.isNotBlank(tagName) && sourceTagSet.contains(tagName.trim().toLowerCase())) {
                sharedTagList.add(tagName.trim());
            }
        }
        return sharedTagList;
    }

    /**
     * 向量检索无命中时，按 ID 顺序兜底候选，保证 Task Match 仍可排序
     */
    private List<RetrievalEngine.RetrievalCandidate> buildFallbackCandidates(int topK, Set<Long> allowedKnowledgeIds) {
        List<RetrievalEngine.RetrievalCandidate> candidateList = new ArrayList<RetrievalEngine.RetrievalCandidate>();
        for (Long allowedKnowledgeId : allowedKnowledgeIds) {
            RetrievalEngine.RetrievalCandidate candidate = new RetrievalEngine.RetrievalCandidate();
            candidate.setKnowledgeId(allowedKnowledgeId);
            candidate.setSimilarityScore(FALLBACK_SIMILARITY_SCORE);
            candidateList.add(candidate);
            if (candidateList.size() >= topK) {
                break;
            }
        }
        return candidateList;
    }

    private List<KnowledgeArtifactEntity> loadArtifactEntities(Long knowledgeId) {
        LambdaQueryWrapper<KnowledgeArtifactEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeArtifactEntity>();
        queryWrapper.eq(KnowledgeArtifactEntity::getKnowledgeId, knowledgeId);
        return knowledgeArtifactMapper.selectList(queryWrapper);
    }

    private KnowledgeRelatedItemVO toRelatedItemView(ScoredRelatedKnowledge scoredRelatedKnowledge) {
        KnowledgeAggregate aggregate = scoredRelatedKnowledge.getAggregate();
        KnowledgeRelatedItemVO itemView = new KnowledgeRelatedItemVO();
        itemView.setId(aggregate.getId());
        itemView.setTitle(aggregate.getTitle());
        itemView.setKnowledgeType(aggregate.getKnowledgeType());
        itemView.setProject(aggregate.getProject());
        itemView.setModule(aggregate.getModule());
        itemView.setRepository(aggregate.getRepository());
        itemView.setTags(aggregate.getTags());
        itemView.setSharedTags(scoredRelatedKnowledge.getSharedTags());
        itemView.setRecallCount(aggregate.getRecallCount());
        itemView.setUpdateTime(aggregate.getUpdateTime());
        itemView.setScore(roundScore(scoredRelatedKnowledge.getScore()));
        itemView.setScoreBreakdown(roundBreakdown(scoredRelatedKnowledge.getScoreBreakdown()));
        itemView.setRelationTypes(scoredRelatedKnowledge.getRelationTypes());
        itemView.setMatchSource(scoredRelatedKnowledge.getMatchSource());
        return itemView;
    }

    private Map<String, Double> roundBreakdown(Map<String, Double> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) {
            return breakdown;
        }
        Map<String, Double> roundedBreakdown = new HashMap<String, Double>();
        for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
            roundedBreakdown.put(entry.getKey(), roundScore(entry.getValue()));
        }
        return roundedBreakdown;
    }

    private Double roundScore(double score) {
        return Math.round(score * 10000D) / 10000D;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        if (limit > 20) {
            throw new BusinessException(400, "limit 最大为 20");
        }
        return limit;
    }

    private void removeKnowledgeId(List<Long> knowledgeIdList, Long knowledgeId) {
        Iterator<Long> iterator = knowledgeIdList.iterator();
        while (iterator.hasNext()) {
            if (knowledgeId.equals(iterator.next())) {
                iterator.remove();
                return;
            }
        }
    }

    /**
     * 内部排序中间结构
     */
    private static class ScoredRelatedKnowledge {

        private KnowledgeAggregate aggregate;

        private double score;

        private Map<String, Double> scoreBreakdown;

        private List<String> sharedTags;

        private List<String> relationTypes;

        private String matchSource;

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

        public Map<String, Double> getScoreBreakdown() {
            return scoreBreakdown;
        }

        public void setScoreBreakdown(Map<String, Double> scoreBreakdown) {
            this.scoreBreakdown = scoreBreakdown;
        }

        public List<String> getSharedTags() {
            return sharedTags;
        }

        public void setSharedTags(List<String> sharedTags) {
            this.sharedTags = sharedTags;
        }

        public List<String> getRelationTypes() {
            return relationTypes;
        }

        public void setRelationTypes(List<String> relationTypes) {
            this.relationTypes = relationTypes;
        }

        public String getMatchSource() {
            return matchSource;
        }

        public void setMatchSource(String matchSource) {
            this.matchSource = matchSource;
        }
    }
}
