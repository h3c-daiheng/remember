package com.zhiyi.memory.governance;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.config.GovernanceProperties;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.GovernanceIssueMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.entity.GovernanceIssueEntity;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Feedback 触发的治理工单：outdated/wrong 累计达阈值时创建过时记忆工单
 */
@Component
public class GovernanceFeedbackHook {

    private static final Logger log = LoggerFactory.getLogger(GovernanceFeedbackHook.class);

    private static final Set<String> OUTDATED_FEEDBACK_TYPES = new HashSet<String>(
            Arrays.asList("outdated", "wrong"));

    private final GovernanceProperties governanceProperties;
    private final MemoryFeedbackMapper memoryFeedbackMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final GovernanceIssueMapper governanceIssueMapper;

    public GovernanceFeedbackHook(GovernanceProperties governanceProperties,
                                  MemoryFeedbackMapper memoryFeedbackMapper,
                                  KnowledgeMapper knowledgeMapper,
                                  GovernanceIssueMapper governanceIssueMapper) {
        this.governanceProperties = governanceProperties;
        this.memoryFeedbackMapper = memoryFeedbackMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.governanceIssueMapper = governanceIssueMapper;
    }

    /**
     * 记录 Feedback 后检查是否需创建过时治理工单
     */
    public void onFeedbackRecorded(Long knowledgeId, String feedbackType) {
        if (!governanceProperties.isFeedbackTriggerEnabled()) {
            return;
        }
        if (knowledgeId == null || StringUtils.isBlank(feedbackType)) {
            return;
        }
        if (!OUTDATED_FEEDBACK_TYPES.contains(feedbackType.trim())) {
            return;
        }

        KnowledgeEntity knowledgeEntity = knowledgeMapper.selectById(knowledgeId);
        if (knowledgeEntity == null
                || knowledgeEntity.getLifecycleStatus() == null
                || knowledgeEntity.getLifecycleStatus().intValue() != MemoryConstants.LIFECYCLE_PUBLISHED) {
            return;
        }

        int negativeFeedbackCount = countOutdatedFeedback(knowledgeId);
        if (negativeFeedbackCount < governanceProperties.getOutdatedFeedbackThreshold()) {
            return;
        }

        if (hasOpenOutdatedIssue(knowledgeEntity.getWorkspaceId(), knowledgeId)) {
            return;
        }

        GovernanceIssueEntity issueEntity = new GovernanceIssueEntity();
        issueEntity.setWorkspaceId(knowledgeEntity.getWorkspaceId());
        issueEntity.setIssueType(GovernanceConstants.ISSUE_TYPE_OUTDATED);
        issueEntity.setStatus(GovernanceConstants.ISSUE_STATUS_OPEN);
        issueEntity.setPrimaryKnowledgeId(knowledgeId);
        issueEntity.setRelatedKnowledgeIds(JSONUtil.toJsonStr(new java.util.ArrayList<Long>()));
        issueEntity.setKnowledgeType(StringUtils.defaultString(
                knowledgeEntity.getKnowledgeType(), MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        issueEntity.setSimilarityScore((double) negativeFeedbackCount / 10D);
        issueEntity.setSuggestedAction(GovernanceConstants.ACTION_DEPRECATE);

        JSONObject metadata = new JSONObject();
        metadata.set("outdatedFeedbackCount", negativeFeedbackCount);
        metadata.set("latestFeedbackType", feedbackType);
        metadata.set("trigger", "feedback_threshold");
        issueEntity.setIssueMetadataJson(metadata.toString());
        governanceIssueMapper.insert(issueEntity);
        log.info("Feedback 触发过时治理工单 knowledgeId={} negativeCount={}", knowledgeId, negativeFeedbackCount);
    }

    private int countOutdatedFeedback(Long knowledgeId) {
        LambdaQueryWrapper<MemoryFeedbackEntity> queryWrapper = new LambdaQueryWrapper<MemoryFeedbackEntity>();
        queryWrapper.eq(MemoryFeedbackEntity::getKnowledgeId, knowledgeId)
                .in(MemoryFeedbackEntity::getFeedbackType, OUTDATED_FEEDBACK_TYPES);
        return memoryFeedbackMapper.selectCount(queryWrapper).intValue();
    }

    private boolean hasOpenOutdatedIssue(String workspaceId, Long knowledgeId) {
        LambdaQueryWrapper<GovernanceIssueEntity> queryWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        queryWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceIssueEntity::getIssueType, GovernanceConstants.ISSUE_TYPE_OUTDATED)
                .eq(GovernanceIssueEntity::getStatus, GovernanceConstants.ISSUE_STATUS_OPEN)
                .eq(GovernanceIssueEntity::getPrimaryKnowledgeId, knowledgeId);
        return governanceIssueMapper.selectCount(queryWrapper) > 0;
    }
}
