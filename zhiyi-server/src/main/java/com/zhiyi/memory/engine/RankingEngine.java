package com.zhiyi.memory.engine;

import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import com.zhiyi.memory.util.TextSimilarityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排序引擎：意图匹配与项目上下文并重，结合相似度、可信度、时效与反馈。
 * 弱语义命中时压制 typeBoost，避免同仓 UI Rule 靠项目匹配霸榜。
 */
@Component
public class RankingEngine {

    /** 相似度低于该阈值时线性压制 typeBoost */
    private static final double TYPE_BOOST_SIMILARITY_FLOOR = 0.25D;

    private final MemoryFeedbackMapper memoryFeedbackMapper;

    public RankingEngine(MemoryFeedbackMapper memoryFeedbackMapper) {
        this.memoryFeedbackMapper = memoryFeedbackMapper;
    }

    /**
     * 计算最终排序分数（无图谱扩展时使用 graphProximity=1）
     */
    public RankingScore rank(KnowledgeAggregate knowledgeAggregate,
                             RecallContext recallContext,
                             double similarityScore,
                             List<KnowledgeArtifactEntity> artifactEntities) {
        return rank(knowledgeAggregate, recallContext, similarityScore, artifactEntities, 1.0D);
    }

    /**
     * 计算最终排序分数，可传入 graphProximity 增强图谱邻接经验
     */
    public RankingScore rank(KnowledgeAggregate knowledgeAggregate,
                             RecallContext recallContext,
                             double similarityScore,
                             List<KnowledgeArtifactEntity> artifactEntities,
                             double graphProximity) {
        RankingScore rankingScore = new RankingScore();
        double taskMatchScore = calculateTaskMatchScore(knowledgeAggregate, recallContext);
        double trustScore = calculateTrustScore(artifactEntities);
        double freshnessScore = calculateFreshnessScore(knowledgeAggregate.getUpdateTime());
        double feedbackScore = calculateFeedbackScore(knowledgeAggregate.getId());
        double typeBoost = calculateTypeBoost(knowledgeAggregate.getKnowledgeType());
        double effectiveTypeBoost = calculateEffectiveTypeBoost(typeBoost, similarityScore);
        double safeGraphProximity = graphProximity <= 0D ? 1.0D : graphProximity;

        // 线性 taskMatch：保留项目加分，但不再平方碾压跨站语义命中
        double finalScore = taskMatchScore
                * similarityScore
                * trustScore
                * freshnessScore
                * feedbackScore
                * effectiveTypeBoost
                * safeGraphProximity;

        Map<String, Double> breakdown = new HashMap<String, Double>();
        breakdown.put("taskMatch", taskMatchScore);
        breakdown.put("similarity", similarityScore);
        breakdown.put("trust", trustScore);
        breakdown.put("freshness", freshnessScore);
        breakdown.put("feedback", feedbackScore);
        breakdown.put("typeBoost", effectiveTypeBoost);
        breakdown.put("graphProximity", safeGraphProximity);

        rankingScore.setFinalScore(finalScore);
        rankingScore.setBreakdown(breakdown);
        return rankingScore;
    }

    /**
     * Task Match：意图匹配权重提高，项目/模块/仓库权重下调，避免上下文虚高。
     */
    private double calculateTaskMatchScore(KnowledgeAggregate knowledgeAggregate, RecallContext recallContext) {
        double score = 0D;
        score += matchText(recallContext.getProject(), knowledgeAggregate.getProject()) * 0.15D;
        score += matchText(recallContext.getRepository(), knowledgeAggregate.getRepository()) * 0.10D;
        score += matchText(recallContext.getModule(), knowledgeAggregate.getModule()) * 0.15D;
        score += matchFile(recallContext, knowledgeAggregate) * 0.15D;
        score += matchText(recallContext.getLanguage(), knowledgeAggregate.getLanguage()) * 0.05D;
        score += matchText(recallContext.getFramework(), knowledgeAggregate.getFramework()) * 0.05D;
        score += matchTaskText(recallContext.getTask(), knowledgeAggregate.getTitle()) * 0.35D;
        return Math.min(1D, score);
    }

    private double matchText(String left, String right) {
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return 0D;
        }
        if (StringUtils.equalsIgnoreCase(left, right)) {
            return 1D;
        }
        if (StringUtils.containsIgnoreCase(left, right) || StringUtils.containsIgnoreCase(right, left)) {
            return 0.7D;
        }
        return 0D;
    }

    private double matchFile(RecallContext recallContext, KnowledgeAggregate knowledgeAggregate) {
        if (recallContext.getModifiedFiles() == null || recallContext.getModifiedFiles().isEmpty()) {
            return 0D;
        }
        String module = knowledgeAggregate.getModule();
        if (StringUtils.isBlank(module)) {
            return 0D;
        }
        for (String fileName : recallContext.getModifiedFiles()) {
            if (StringUtils.containsIgnoreCase(fileName, module)) {
                return 1D;
            }
        }
        return 0D;
    }

    private double matchTaskText(String task, String title) {
        if (StringUtils.isBlank(task) || StringUtils.isBlank(title)) {
            return 0D;
        }
        return TextSimilarityUtil.cosineSimilarity(task, title);
    }

    /**
     * 弱语义时压制 typeBoost：similarity 低于 0.25 时线性收敛到 1.0
     */
    private double calculateEffectiveTypeBoost(double typeBoost, double similarityScore) {
        if (similarityScore >= TYPE_BOOST_SIMILARITY_FLOOR) {
            return typeBoost;
        }
        if (similarityScore <= 0D) {
            return 1.0D;
        }
        return 1.0D + (typeBoost - 1.0D) * (similarityScore / TYPE_BOOST_SIMILARITY_FLOOR);
    }

    /**
     * 可信度：evidence 类型 Artifact 越多分数越高
     */
    private double calculateTrustScore(List<KnowledgeArtifactEntity> artifactEntities) {
        if (artifactEntities == null || artifactEntities.isEmpty()) {
            return 0.6D;
        }
        int evidenceCount = 0;
        for (KnowledgeArtifactEntity artifactEntity : artifactEntities) {
            if ("evidence".equals(artifactEntity.getArtifactRole())) {
                evidenceCount++;
            }
        }
        return Math.min(1D, 0.6D + evidenceCount * 0.1D);
    }

    /**
     * 时效性：180 天内线性衰减
     */
    private double calculateFreshnessScore(Date updateTime) {
        if (updateTime == null) {
            return 0.8D;
        }
        long ageMillis = System.currentTimeMillis() - updateTime.getTime();
        long maxAgeMillis = 180L * 24L * 60L * 60L * 1000L;
        if (ageMillis <= 0L) {
            return 1D;
        }
        if (ageMillis >= maxAgeMillis) {
            return 0.5D;
        }
        return 1D - (ageMillis * 0.5D / maxAgeMillis);
    }

    /**
     * 反馈分：helpful 加分，outdated/wrong 降权
     */
    private double calculateFeedbackScore(Long knowledgeId) {
        LambdaQueryWrapper<MemoryFeedbackEntity> queryWrapper = new LambdaQueryWrapper<MemoryFeedbackEntity>();
        queryWrapper.eq(MemoryFeedbackEntity::getKnowledgeId, knowledgeId);
        List<MemoryFeedbackEntity> feedbackList = memoryFeedbackMapper.selectList(queryWrapper);
        if (feedbackList.isEmpty()) {
            return 1D;
        }
        double score = 1D;
        for (MemoryFeedbackEntity feedbackEntity : feedbackList) {
            if ("helpful".equals(feedbackEntity.getFeedbackType()) || "used".equals(feedbackEntity.getFeedbackType())) {
                score += 0.05D;
            }
            if ("not_helpful".equals(feedbackEntity.getFeedbackType())) {
                score -= 0.05D;
            }
            if ("outdated".equals(feedbackEntity.getFeedbackType()) || "wrong".equals(feedbackEntity.getFeedbackType())) {
                score -= 0.15D;
            }
        }
        return Math.max(0.3D, Math.min(1.2D, score));
    }

    /**
     * 知识类型加权：Rule > Workflow > Decision > Experience
     */
    private double calculateTypeBoost(String knowledgeType) {
        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(knowledgeType)) {
            return 1.25D;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(knowledgeType)) {
            return 1.15D;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(knowledgeType)) {
            return 1.1D;
        }
        return 1.0D;
    }

    /**
     * 排序得分封装
     */
    public static class RankingScore {

        private double finalScore;

        private Map<String, Double> breakdown;

        public double getFinalScore() {
            return finalScore;
        }

        public void setFinalScore(double finalScore) {
            this.finalScore = finalScore;
        }

        public Map<String, Double> getBreakdown() {
            return breakdown;
        }

        public void setBreakdown(Map<String, Double> breakdown) {
            this.breakdown = breakdown;
        }
    }
}
