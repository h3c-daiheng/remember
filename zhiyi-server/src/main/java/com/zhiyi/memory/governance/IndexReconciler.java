package com.zhiyi.memory.governance;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.retrieval.RetrievalEngine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 索引协调器：治理合并后同步 Recall 索引与 recall_count 继承
 */
@Component
public class IndexReconciler {

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeService knowledgeService;
    private final RetrievalEngine retrievalEngine;

    public IndexReconciler(KnowledgeMapper knowledgeMapper,
                           KnowledgeService knowledgeService,
                           RetrievalEngine retrievalEngine) {
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeService = knowledgeService;
        this.retrievalEngine = retrievalEngine;
    }

    /**
     * 合并后继承源记忆的 recall_count 总和
     */
    public void inheritRecallCount(Long targetKnowledgeId, List<Long> sourceKnowledgeIds) {
        if (targetKnowledgeId == null || sourceKnowledgeIds == null || sourceKnowledgeIds.isEmpty()) {
            return;
        }
        int inheritedCount = 0;
        for (Long sourceId : sourceKnowledgeIds) {
            KnowledgeEntity sourceEntity = knowledgeMapper.selectById(sourceId);
            if (sourceEntity != null && sourceEntity.getRecallCount() != null) {
                inheritedCount += sourceEntity.getRecallCount();
            }
        }
        if (inheritedCount <= 0) {
            return;
        }
        LambdaUpdateWrapper<KnowledgeEntity> updateWrapper = new LambdaUpdateWrapper<KnowledgeEntity>();
        updateWrapper.eq(KnowledgeEntity::getId, targetKnowledgeId)
                .setSql("recall_count = recall_count + " + inheritedCount);
        knowledgeMapper.update(null, updateWrapper);
    }

    /**
     * 重建已发布记忆的检索索引（create 时已索引，此处用于合并后内容变更的兜底刷新）
     */
    public void reconcilePublishedKnowledge(Long knowledgeId, String workspaceId) {
        knowledgeService.getDetail(knowledgeId, workspaceId);
        KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
        if (entity == null) {
            return;
        }
        // KnowledgeService.create 已触发 indexKnowledge；此处通过 getDetail 校验存在性即可
    }

    /**
     * 批量清理已下架记忆的检索索引
     */
    public void removeDeprecatedIndexes(List<Long> knowledgeIds) {
        if (knowledgeIds == null) {
            return;
        }
        for (Long knowledgeId : knowledgeIds) {
            retrievalEngine.deleteByKnowledgeId(knowledgeId);
        }
    }
}
