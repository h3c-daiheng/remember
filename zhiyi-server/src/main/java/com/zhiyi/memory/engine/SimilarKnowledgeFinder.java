package com.zhiyi.memory.engine;

import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.CaptureSimilarKnowledgeHint;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.retrieval.RetrievalEngine;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 相似知识检索：供 Capture 归类建议与 AI Review 发布前门禁复用
 */
@Component
public class SimilarKnowledgeFinder {

    private static final Logger log = LoggerFactory.getLogger(SimilarKnowledgeFinder.class);

    /** 参与相似检索的已发布知识类型 */
    private static final List<String> SIMILAR_KNOWLEDGE_TYPES = Arrays.asList(
            MemoryConstants.KNOWLEDGE_TYPE_RULE,
            MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW,
            MemoryConstants.KNOWLEDGE_TYPE_DECISION,
            MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE
    );

    /** 候选召回时的最低文本相似度（低于此值不进入提示列表） */
    private static final double CANDIDATE_MIN_SIMILARITY = 0.15D;

    /** 默认返回 TopK */
    private static final int DEFAULT_TOP_K = 3;

    private final KnowledgeService knowledgeService;
    private final RetrievalEngine retrievalEngine;

    public SimilarKnowledgeFinder(KnowledgeService knowledgeService, RetrievalEngine retrievalEngine) {
        this.knowledgeService = knowledgeService;
        this.retrievalEngine = retrievalEngine;
    }

    /**
     * 检索工作空间内与草稿相似的已发布知识
     */
    public List<CaptureSimilarKnowledgeHint> findSimilar(KnowledgeDraftContent content, String workspaceId) {
        return findSimilar(content, workspaceId, DEFAULT_TOP_K);
    }

    /**
     * 检索工作空间内与草稿相似的已发布知识，可指定 TopK
     */
    public List<CaptureSimilarKnowledgeHint> findSimilar(KnowledgeDraftContent content,
                                                         String workspaceId,
                                                         int topK) {
        String queryText = buildDraftSearchText(content);
        if (StringUtils.isBlank(queryText) || StringUtils.isBlank(workspaceId)) {
            return new ArrayList<CaptureSimilarKnowledgeHint>();
        }
        int safeTopK = Math.max(topK, 1);
        List<Long> candidateIdList = knowledgeService.listPublishedKnowledgeIds(workspaceId, SIMILAR_KNOWLEDGE_TYPES);
        Set<Long> allowedKnowledgeIds = retrievalEngine.toAllowedKnowledgeIdSet(candidateIdList);
        List<RetrievalEngine.RetrievalCandidate> candidateList = retrievalEngine.search(
                queryText, null, safeTopK, allowedKnowledgeIds);

        List<CaptureSimilarKnowledgeHint> hintList = new ArrayList<CaptureSimilarKnowledgeHint>();
        for (RetrievalEngine.RetrievalCandidate candidate : candidateList) {
            if (candidate.getSimilarityScore() < CANDIDATE_MIN_SIMILARITY) {
                continue;
            }
            try {
                KnowledgeAggregate aggregate = knowledgeService.getDetail(candidate.getKnowledgeId(), workspaceId);
                CaptureSimilarKnowledgeHint hint = new CaptureSimilarKnowledgeHint();
                hint.setKnowledgeId(aggregate.getId());
                hint.setKnowledgeType(aggregate.getKnowledgeType());
                hint.setTitle(StringUtils.defaultString(aggregate.getTitle()));
                hint.setSimilarityScore(candidate.getSimilarityScore());
                hintList.add(hint);
            } catch (Exception exception) {
                log.debug("加载相似知识失败 knowledgeId={} message={}",
                        candidate.getKnowledgeId(), exception.getMessage());
            }
        }
        return hintList;
    }

    /**
     * 判断相似列表是否命中门禁阈值（任一候选达到阈值即命中）
     */
    public boolean hasSimilarHit(List<CaptureSimilarKnowledgeHint> similarKnowledgeList, double threshold) {
        if (similarKnowledgeList == null || similarKnowledgeList.isEmpty()) {
            return false;
        }
        double safeThreshold = threshold;
        for (CaptureSimilarKnowledgeHint hint : similarKnowledgeList) {
            if (hint != null && hint.getSimilarityScore() >= safeThreshold) {
                return true;
            }
        }
        return false;
    }

    /**
     * 过滤出达到门禁阈值的相似知识，供前端告警展示
     */
    public List<CaptureSimilarKnowledgeHint> filterAboveThreshold(List<CaptureSimilarKnowledgeHint> similarKnowledgeList,
                                                                  double threshold) {
        List<CaptureSimilarKnowledgeHint> filteredList = new ArrayList<CaptureSimilarKnowledgeHint>();
        if (similarKnowledgeList == null || similarKnowledgeList.isEmpty()) {
            return filteredList;
        }
        for (CaptureSimilarKnowledgeHint hint : similarKnowledgeList) {
            if (hint != null && hint.getSimilarityScore() >= threshold) {
                filteredList.add(hint);
            }
        }
        return filteredList;
    }

    /**
     * 拼接草稿标题与前几条 Fact 文本作为检索 Query
     */
    public String buildDraftSearchText(KnowledgeDraftContent content) {
        if (content == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (StringUtils.isNotBlank(content.getTitle())) {
            builder.append(content.getTitle()).append(' ');
        }
        if (content.getFacts() != null) {
            int appendedCount = 0;
            for (FactBlock factBlock : content.getFacts()) {
                if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                    continue;
                }
                builder.append(factBlock.getText()).append(' ');
                appendedCount++;
                if (appendedCount >= 3) {
                    break;
                }
            }
        }
        return builder.toString().trim();
    }

    /**
     * 检索与已发布知识相似的其它已发布知识，供治理重复扫描使用
     *
     * @param sourceAggregate 源知识
     * @param workspaceId     工作空间
     * @param topK            返回条数
     * @param threshold       最低相似度
     */
    public List<CaptureSimilarKnowledgeHint> findSimilarForKnowledge(KnowledgeAggregate sourceAggregate,
                                                                     String workspaceId,
                                                                     int topK,
                                                                     double threshold) {
        if (sourceAggregate == null || sourceAggregate.getId() == null || StringUtils.isBlank(workspaceId)) {
            return new ArrayList<CaptureSimilarKnowledgeHint>();
        }
        String queryText = buildKnowledgeSearchText(sourceAggregate);
        if (StringUtils.isBlank(queryText)) {
            return new ArrayList<CaptureSimilarKnowledgeHint>();
        }
        List<String> typeFilter = new ArrayList<String>();
        if (StringUtils.isNotBlank(sourceAggregate.getKnowledgeType())) {
            typeFilter.add(sourceAggregate.getKnowledgeType());
        } else {
            typeFilter.addAll(SIMILAR_KNOWLEDGE_TYPES);
        }
        int safeTopK = Math.max(topK, 1);
        List<Long> candidateIdList = knowledgeService.listPublishedKnowledgeIds(workspaceId, typeFilter);
        Set<Long> allowedKnowledgeIds = retrievalEngine.toAllowedKnowledgeIdSet(candidateIdList);
        allowedKnowledgeIds.remove(sourceAggregate.getId());

        List<RetrievalEngine.RetrievalCandidate> candidateList = retrievalEngine.search(
                queryText, null, safeTopK + 1, allowedKnowledgeIds);

        List<CaptureSimilarKnowledgeHint> hintList = new ArrayList<CaptureSimilarKnowledgeHint>();
        for (RetrievalEngine.RetrievalCandidate candidate : candidateList) {
            if (candidate.getKnowledgeId().equals(sourceAggregate.getId())) {
                continue;
            }
            if (candidate.getSimilarityScore() < threshold) {
                continue;
            }
            try {
                KnowledgeAggregate aggregate = knowledgeService.getDetail(candidate.getKnowledgeId(), workspaceId);
                CaptureSimilarKnowledgeHint hint = new CaptureSimilarKnowledgeHint();
                hint.setKnowledgeId(aggregate.getId());
                hint.setKnowledgeType(aggregate.getKnowledgeType());
                hint.setTitle(StringUtils.defaultString(aggregate.getTitle()));
                hint.setSimilarityScore(candidate.getSimilarityScore());
                hintList.add(hint);
            } catch (Exception exception) {
                log.debug("治理扫描加载相似知识失败 knowledgeId={} message={}",
                        candidate.getKnowledgeId(), exception.getMessage());
            }
        }
        return hintList;
    }

    /**
     * 拼接已发布知识标题与前几条 Fact 作为检索 Query
     */
    public String buildKnowledgeSearchText(KnowledgeAggregate aggregate) {
        if (aggregate == null) {
            return "";
        }
        KnowledgeDraftContent draftContent = new KnowledgeDraftContent();
        draftContent.setTitle(aggregate.getTitle());
        draftContent.setFacts(aggregate.getFacts());
        return buildDraftSearchText(draftContent);
    }
}
