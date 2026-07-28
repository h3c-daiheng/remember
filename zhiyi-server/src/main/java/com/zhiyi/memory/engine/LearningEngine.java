package com.zhiyi.memory.engine;

import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.domain.FeedbackRequest;
import com.zhiyi.memory.domain.RecallContext;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import com.zhiyi.memory.governance.GovernanceFeedbackHook;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Learning 引擎：收集 Recall 反馈，MVP 阶段入库并供 Ranking 使用
 */
@Service
public class LearningEngine {

    private final MemoryFeedbackMapper memoryFeedbackMapper;
    private final GovernanceFeedbackHook governanceFeedbackHook;

    public LearningEngine(MemoryFeedbackMapper memoryFeedbackMapper,
                          GovernanceFeedbackHook governanceFeedbackHook) {
        this.memoryFeedbackMapper = memoryFeedbackMapper;
        this.governanceFeedbackHook = governanceFeedbackHook;
    }

    /**
     * 记录 Agent 或用户对 Recall 结果的反馈
     */
    public void recordFeedback(FeedbackRequest feedbackRequest, RecallContext recallContext, Long actorId) {
        if (feedbackRequest == null || feedbackRequest.getKnowledgeId() == null) {
            throw new BusinessException(400, "knowledgeId 不能为空");
        }
        if (StringUtils.isBlank(feedbackRequest.getFeedbackType())) {
            throw new BusinessException(400, "feedbackType 不能为空");
        }
        MemoryFeedbackEntity feedbackEntity = new MemoryFeedbackEntity();
        feedbackEntity.setKnowledgeId(feedbackRequest.getKnowledgeId());
        feedbackEntity.setRecallSession(feedbackRequest.getSessionId());
        feedbackEntity.setFeedbackType(feedbackRequest.getFeedbackType());
        feedbackEntity.setContextJson(recallContext == null ? null : MemoryJsonUtil.toJson(recallContext));
        feedbackEntity.setActorId(actorId);
        memoryFeedbackMapper.insert(feedbackEntity);
        governanceFeedbackHook.onFeedbackRecorded(feedbackRequest.getKnowledgeId(), feedbackRequest.getFeedbackType());
    }
}
