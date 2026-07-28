package com.zhiyi.memory.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.KnowledgeVectorRefMapper;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeVectorRefEntity;
import com.zhiyi.memory.util.TextSimilarityUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索引擎：MVP 基于 knowledge_vector_ref 中的 chunk_text 做文本相似度召回，按工作空间过滤
 */
@Component
public class RetrievalEngine {

    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;

    public RetrievalEngine(KnowledgeVectorRefMapper knowledgeVectorRefMapper) {
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
    }

    /**
     * 为已发布知识建立检索索引
     */
    public void indexKnowledge(KnowledgeEntity knowledgeEntity, String indexText) {
        deleteByKnowledgeId(knowledgeEntity.getId());
        KnowledgeVectorRefEntity vectorRefEntity = new KnowledgeVectorRefEntity();
        vectorRefEntity.setKnowledgeId(knowledgeEntity.getId());
        vectorRefEntity.setChunkIndex(0);
        vectorRefEntity.setChunkText(indexText);
        vectorRefEntity.setVectorId("knowledge-" + knowledgeEntity.getId() + "-0");
        vectorRefEntity.setModelName(MemoryConstants.DEFAULT_TEXT_MODEL);
        knowledgeVectorRefMapper.insert(vectorRefEntity);
    }

    /**
     * 删除知识对应的检索索引
     */
    public void deleteByKnowledgeId(Long knowledgeId) {
        LambdaQueryWrapper<KnowledgeVectorRefEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeVectorRefEntity>();
        queryWrapper.eq(KnowledgeVectorRefEntity::getKnowledgeId, knowledgeId);
        knowledgeVectorRefMapper.delete(queryWrapper);
    }

    /**
     * 根据 Query 文本召回候选 knowledgeId 及相似度，仅检索指定工作空间内的经验
     */
    public List<RetrievalCandidate> search(String queryText, RecallContext recallContext, int topK,
                                           Set<Long> allowedKnowledgeIds) {
        if (StringUtils.isBlank(queryText)) {
            return Collections.emptyList();
        }
        if (allowedKnowledgeIds == null || allowedKnowledgeIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<KnowledgeVectorRefEntity> allRefs = knowledgeVectorRefMapper.selectList(null);
        List<RetrievalCandidate> candidates = new ArrayList<RetrievalCandidate>();
        for (KnowledgeVectorRefEntity vectorRef : allRefs) {
            if (!allowedKnowledgeIds.contains(vectorRef.getKnowledgeId())) {
                continue;
            }
            double similarity = TextSimilarityUtil.cosineSimilarity(queryText, vectorRef.getChunkText());
            if (similarity <= 0D) {
                continue;
            }
            RetrievalCandidate candidate = new RetrievalCandidate();
            candidate.setKnowledgeId(vectorRef.getKnowledgeId());
            candidate.setSimilarityScore(similarity);
            candidates.add(candidate);
        }
        Collections.sort(candidates, new Comparator<RetrievalCandidate>() {
            @Override
            public int compare(RetrievalCandidate left, RetrievalCandidate right) {
                return Double.compare(right.getSimilarityScore(), left.getSimilarityScore());
            }
        });
        if (candidates.size() > topK) {
            return new ArrayList<RetrievalCandidate>(candidates.subList(0, topK));
        }
        return candidates;
    }

    /**
     * 将知识 ID 列表转换为检索过滤集合
     */
    public Set<Long> toAllowedKnowledgeIdSet(List<Long> knowledgeIdList) {
        if (knowledgeIdList == null || knowledgeIdList.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<Long>(knowledgeIdList);
    }

    /**
     * 检索候选结果
     */
    public static class RetrievalCandidate {

        private Long knowledgeId;

        private double similarityScore;

        public Long getKnowledgeId() {
            return knowledgeId;
        }

        public void setKnowledgeId(Long knowledgeId) {
            this.knowledgeId = knowledgeId;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public void setSimilarityScore(double similarityScore) {
            this.similarityScore = similarityScore;
        }
    }
}
