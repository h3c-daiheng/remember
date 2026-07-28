package com.zhiyi.memory.engine;

import com.zhiyi.common.BusinessException;
import com.zhiyi.config.AiReviewProperties;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.SystemEventMapper;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.domain.SystemEventRequest;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.entity.SystemEventEntity;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Capture 引擎：消费统一 Event，生成待 Review 的 Capture 草稿
 */
@Service
public class CaptureEngine {

    private final SystemEventMapper systemEventMapper;
    private final CaptureDraftMapper captureDraftMapper;
    private final SummarizeEngine summarizeEngine;
    private final AiReviewAsyncService aiReviewAsyncService;
    private final AiReviewProperties aiReviewProperties;

    public CaptureEngine(SystemEventMapper systemEventMapper,
                         CaptureDraftMapper captureDraftMapper,
                         SummarizeEngine summarizeEngine,
                         AiReviewAsyncService aiReviewAsyncService,
                         AiReviewProperties aiReviewProperties) {
        this.systemEventMapper = systemEventMapper;
        this.captureDraftMapper = captureDraftMapper;
        this.summarizeEngine = summarizeEngine;
        this.aiReviewAsyncService = aiReviewAsyncService;
        this.aiReviewProperties = aiReviewProperties;
    }

    /**
     * Submit API：写入 Event 并同步生成 Capture 草稿
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(SystemEventRequest eventRequest, Long creatorId) {
        if (eventRequest == null || StringUtils.isBlank(eventRequest.getType())) {
            throw new BusinessException(400, "事件类型不能为空");
        }
        String knowledgeType = resolveKnowledgeType(eventRequest.getKnowledgeType());
        eventRequest.setKnowledgeType(knowledgeType);
        enrichSubmitMetadata(eventRequest, knowledgeType);

        SystemEventEntity eventEntity = buildEventEntity(eventRequest, creatorId);
        systemEventMapper.insert(eventEntity);

        try {
            KnowledgeDraftContent draftContent = summarizeEngine.toFactBlocks(eventRequest);
            draftContent.setSubmittedKnowledgeType(knowledgeType);
            CaptureDraftEntity draftEntity = new CaptureDraftEntity();
            draftEntity.setWorkspaceId(eventEntity.getWorkspaceId());
            draftEntity.setEventId(eventEntity.getId());
            draftEntity.setDraftJson(MemoryJsonUtil.toJson(draftContent));
            draftEntity.setReviewStatus(MemoryConstants.REVIEW_PENDING);
            if (aiReviewProperties.isEnabled()) {
                draftEntity.setAiReviewStatus(MemoryConstants.AI_REVIEW_STATUS_QUEUED);
            }
            captureDraftMapper.insert(draftEntity);

            eventEntity.setProcessStatus(MemoryConstants.EVENT_DONE);
            systemEventMapper.updateById(eventEntity);

            scheduleAiReviewAfterCommit(draftEntity.getId());

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("eventId", eventEntity.getId());
            result.put("draftId", draftEntity.getId());
            result.put("knowledgeType", knowledgeType);
            result.put("status", "pending_review");
            if (aiReviewProperties.isEnabled()) {
                result.put("aiReviewStatus", "queued");
            }
            return result;
        } catch (BusinessException businessException) {
            eventEntity.setProcessStatus(MemoryConstants.EVENT_FAILED);
            systemEventMapper.updateById(eventEntity);
            throw businessException;
        } catch (Exception exception) {
            eventEntity.setProcessStatus(MemoryConstants.EVENT_FAILED);
            systemEventMapper.updateById(eventEntity);
            throw new BusinessException(500, "Capture 草稿生成失败");
        }
    }

    /**
     * 解析 Agent 声明的知识类型，默认 experience
     */
    private String resolveKnowledgeType(String knowledgeType) {
        String normalizedType = StringUtils.trimToEmpty(knowledgeType);
        if (StringUtils.isBlank(normalizedType)) {
            return MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE;
        }
        if (!MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(normalizedType)
                && !MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(normalizedType)
                && !MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(normalizedType)
                && !MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(normalizedType)) {
            throw new BusinessException(400, "knowledgeType 仅支持 experience、rule、workflow、decision");
        }
        return normalizedType;
    }

    /**
     * 写入 metadata.routeHint，供 Review 路由建议与闭环追踪使用
     */
    private void enrichSubmitMetadata(SystemEventRequest eventRequest, String knowledgeType) {
        Map<String, Object> metadata = eventRequest.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<String, Object>();
            eventRequest.setMetadata(metadata);
        }
        metadata.put("submittedKnowledgeType", knowledgeType);
        metadata.put("routeHint", resolveRouteHint(knowledgeType));
    }

    /**
     * 将 knowledgeType 映射为 Capture 路由 hint
     */
    private String resolveRouteHint(String knowledgeType) {
        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(knowledgeType)) {
            return MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(knowledgeType)) {
            return MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(knowledgeType)) {
            return MemoryConstants.REVIEW_ACTION_ROUTE_TO_DECISION;
        }
        return MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE;
    }

    private SystemEventEntity buildEventEntity(SystemEventRequest eventRequest, Long creatorId) {
        SystemEventEntity eventEntity = new SystemEventEntity();
        eventEntity.setWorkspaceId(eventRequest.getWorkspaceId());
        eventEntity.setEventType(eventRequest.getType());
        eventEntity.setActor(eventRequest.getActor());
        eventEntity.setWorkspace(eventRequest.getWorkspace());
        eventEntity.setRepository(eventRequest.getRepository());
        eventEntity.setModule(eventRequest.getModule());
        eventEntity.setEventTime(eventRequest.getTime() == null ? new Date() : eventRequest.getTime());
        eventEntity.setArtifactsJson(MemoryJsonUtil.toJson(eventRequest.getArtifacts()));
        eventEntity.setMetadataJson(MemoryJsonUtil.toJson(eventRequest.getMetadata()));
        eventEntity.setPayloadJson(MemoryJsonUtil.toJson(eventRequest.getPayload()));
        eventEntity.setProcessStatus(MemoryConstants.EVENT_PENDING);
        eventEntity.setCreatorId(creatorId);
        return eventEntity;
    }

    /**
     * 事务提交后再触发 AI Review，避免异步线程读不到未提交草稿
     */
    private void scheduleAiReviewAfterCommit(final Long draftId) {
        if (!aiReviewProperties.isEnabled() || draftId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiReviewAsyncService.reviewDraftAsync(draftId);
                }
            });
            return;
        }
        aiReviewAsyncService.reviewDraftAsync(draftId);
    }
}
