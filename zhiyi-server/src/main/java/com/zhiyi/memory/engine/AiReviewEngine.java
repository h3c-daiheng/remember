package com.zhiyi.memory.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.config.AiReviewProperties;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.CaptureAiReviewMapper;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.domain.CaptureAiReviewView;
import com.zhiyi.memory.domain.CaptureDraftView;
import com.zhiyi.memory.domain.CaptureRejectRequest;
import com.zhiyi.memory.domain.CaptureRouteChecklistHint;
import com.zhiyi.memory.domain.CaptureRouteRequest;
import com.zhiyi.memory.domain.CaptureRouteSuggestion;
import com.zhiyi.memory.domain.CaptureSimilarKnowledgeHint;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.entity.CaptureAiReviewEntity;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.knowledge.CaptureService;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Capture AI Review 引擎：提交后自动审核，相似记忆命中则转人工，否则按路由建议自动执行
 */
@Service
public class AiReviewEngine {

    private static final Logger log = LoggerFactory.getLogger(AiReviewEngine.class);

    private final AiReviewProperties aiReviewProperties;
    private final CaptureAiReviewMapper captureAiReviewMapper;
    private final CaptureDraftMapper captureDraftMapper;
    private final SimilarKnowledgeFinder similarKnowledgeFinder;
    private final CaptureRouteEngine captureRouteEngine;
    private final CaptureService captureService;

    public AiReviewEngine(AiReviewProperties aiReviewProperties,
                          CaptureAiReviewMapper captureAiReviewMapper,
                          CaptureDraftMapper captureDraftMapper,
                          SimilarKnowledgeFinder similarKnowledgeFinder,
                          CaptureRouteEngine captureRouteEngine,
                          @Lazy CaptureService captureService) {
        this.aiReviewProperties = aiReviewProperties;
        this.captureAiReviewMapper = captureAiReviewMapper;
        this.captureDraftMapper = captureDraftMapper;
        this.similarKnowledgeFinder = similarKnowledgeFinder;
        this.captureRouteEngine = captureRouteEngine;
        this.captureService = captureService;
    }

    /**
     * 对指定草稿执行一次 AI Review（幂等：草稿非待确认时跳过）
     */
    public void reviewDraft(Long draftId) {
        reviewDraft(draftId, null);
    }

    /**
     * 对指定草稿执行一次 AI Review（幂等：草稿非待确认时跳过）
     *
     * @param reviewerId 人工触发重跑时的登录用户；自动执行时作为审核人与知识创建者
     */
    public void reviewDraft(Long draftId, Long reviewerId) {
        if (draftId == null) {
            return;
        }
        if (!aiReviewProperties.isEnabled()) {
            log.info("AI Review 未启用，跳过 draftId={}", draftId);
            return;
        }

        CaptureDraftEntity draftEntity = captureDraftMapper.selectById(draftId);
        if (draftEntity == null) {
            log.warn("AI Review 草稿不存在 draftId={}", draftId);
            return;
        }
        if (draftEntity.getReviewStatus() == null
                || draftEntity.getReviewStatus() != MemoryConstants.REVIEW_PENDING) {
            log.info("AI Review 跳过：草稿已处理 draftId={} reviewStatus={}",
                    draftId, draftEntity.getReviewStatus());
            return;
        }

        long startMillis = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        CaptureAiReviewEntity reviewEntity = createRunningRecord(draftEntity, traceId);
        syncDraftAiReviewFields(draftEntity, MemoryConstants.AI_REVIEW_STATUS_RUNNING, null);

        try {
            KnowledgeDraftContent draftContent = MemoryJsonUtil.parseDraftContent(draftEntity.getDraftJson());
            List<CaptureSimilarKnowledgeHint> similarKnowledgeList = similarKnowledgeFinder.findSimilar(
                    draftContent, draftEntity.getWorkspaceId());
            boolean similarHit = similarKnowledgeFinder.hasSimilarHit(
                    similarKnowledgeList, aiReviewProperties.getSimilarThreshold());

            if (similarHit) {
                finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, null,
                        "命中相似记忆门禁，转人工处理", true, startMillis);
                return;
            }

            CaptureDraftView draftView = new CaptureDraftView();
            draftView.setId(draftEntity.getId());
            draftView.setEventId(draftEntity.getEventId());
            draftView.setDraftContent(draftContent);
            draftView.setReviewStatus(draftEntity.getReviewStatus());

            CaptureRouteSuggestion suggestion = captureRouteEngine.suggest(
                    draftView, draftEntity.getWorkspaceId(), false);
            if (suggestion.getSimilarKnowledge() == null || suggestion.getSimilarKnowledge().isEmpty()) {
                suggestion.setSimilarKnowledge(similarKnowledgeList);
            }

            // 二次门禁：suggest 过程中检索到的相似结果也要拦截
            if (similarKnowledgeFinder.hasSimilarHit(
                    suggestion.getSimilarKnowledge(), aiReviewProperties.getSimilarThreshold())) {
                finishEscalate(reviewEntity, draftEntity, suggestion.getSimilarKnowledge(), suggestion,
                        "路由建议阶段命中相似记忆，转人工处理", true, startMillis);
                return;
            }

            executeBySuggestion(reviewEntity, draftEntity, suggestion, startMillis, reviewerId);
        } catch (Exception exception) {
            log.warn("AI Review 失败 draftId={} reviewerId={} message={}",
                    draftId, reviewerId, exception.getMessage(), exception);
            finishFailed(reviewEntity, draftEntity, exception.getMessage(), startMillis);
        }
    }

    /**
     * 查询草稿最新一条 AI Review 记录
     */
    public CaptureAiReviewView getLatestReview(Long draftId, String workspaceId) {
        CaptureAiReviewEntity entity = findLatestEntity(draftId, workspaceId);
        return toView(entity);
    }

    /**
     * 按记录主键查询 AI Review（校验工作空间归属）
     */
    public CaptureAiReviewView getReviewById(Long reviewId, String workspaceId) {
        if (reviewId == null) {
            return null;
        }
        CaptureAiReviewEntity entity = captureAiReviewMapper.selectById(reviewId);
        if (entity == null) {
            return null;
        }
        if (StringUtils.isNotBlank(workspaceId) && !workspaceId.equals(entity.getWorkspaceId())) {
            throw new BusinessException(403, "无权访问该 AI 审查记录");
        }
        return toView(entity);
    }

    /**
     * 按路由建议裁决并自动执行或转人工
     */
    private void executeBySuggestion(CaptureAiReviewEntity reviewEntity,
                                     CaptureDraftEntity draftEntity,
                                     CaptureRouteSuggestion suggestion,
                                     long startMillis,
                                     Long reviewerId) {
        String recommendedAction = suggestion == null ? null : suggestion.getRecommendedAction();
        double confidence = suggestion == null ? 0D : suggestion.getConfidence();
        List<CaptureSimilarKnowledgeHint> similarList = suggestion == null
                ? new ArrayList<CaptureSimilarKnowledgeHint>()
                : suggestion.getSimilarKnowledge();

        if (StringUtils.isBlank(recommendedAction)) {
            finishEscalate(reviewEntity, draftEntity, similarList, suggestion,
                    "缺少推荐动作，转人工处理", false, startMillis);
            return;
        }
        if (MemoryConstants.REVIEW_ACTION_MERGE_TO_RULE.equals(recommendedAction)) {
            finishEscalate(reviewEntity, draftEntity, similarList, suggestion,
                    "建议合并到已有 Rule，转人工处理", false, startMillis);
            return;
        }
        if (confidence < aiReviewProperties.getAutoExecuteConfidenceThreshold()) {
            finishEscalate(reviewEntity, draftEntity, similarList, suggestion,
                    "置信度不足，转人工处理", false, startMillis);
            return;
        }

        if (isApproveAction(recommendedAction)) {
            captureService.approveDraft(draftEntity.getId(), null, reviewerId, draftEntity.getWorkspaceId());
            markDraftReviewComment(draftEntity.getId(),
                    MemoryConstants.AI_REVIEW_COMMENT_PREFIX + recommendedAction);
            finishExecuted(reviewEntity, draftEntity, similarList, suggestion,
                    MemoryConstants.AI_REVIEW_DECISION_APPROVE, true, startMillis);
            return;
        }

        if (MemoryConstants.REVIEW_ACTION_REJECT.equals(recommendedAction)) {
            CaptureRejectRequest rejectRequest = new CaptureRejectRequest();
            rejectRequest.setRejectReason(StringUtils.defaultIfBlank(
                    suggestion.getRecommendedRejectReason(), "REJECT_LOW_QUALITY"));
            rejectRequest.setReviewComment(MemoryConstants.AI_REVIEW_COMMENT_PREFIX
                    + rejectRequest.getRejectReason());
            captureService.rejectDraft(draftEntity.getId(), rejectRequest, reviewerId, draftEntity.getWorkspaceId());
            finishExecuted(reviewEntity, draftEntity, similarList, suggestion,
                    MemoryConstants.AI_REVIEW_DECISION_REJECT, true, startMillis);
            return;
        }

        if (isRouteAction(recommendedAction)) {
            if (!aiReviewProperties.isAutoRouteEnabled()) {
                finishEscalate(reviewEntity, draftEntity, similarList, suggestion,
                        "自动路由未启用，转人工处理", false, startMillis);
                return;
            }
            CaptureRouteRequest routeRequest = new CaptureRouteRequest();
            routeRequest.setTargetType(resolveRouteTargetType(recommendedAction, suggestion));
            routeRequest.setRejectReason(suggestion.getRecommendedRejectReason());
            routeRequest.setReviewComment(MemoryConstants.AI_REVIEW_COMMENT_PREFIX + recommendedAction);
            captureService.routeDraft(draftEntity.getId(), routeRequest, reviewerId, draftEntity.getWorkspaceId());
            finishExecuted(reviewEntity, draftEntity, similarList, suggestion,
                    MemoryConstants.AI_REVIEW_DECISION_ROUTE, true, startMillis);
            return;
        }

        finishEscalate(reviewEntity, draftEntity, similarList, suggestion,
                "未识别的推荐动作，转人工处理", false, startMillis);
    }

    private boolean isApproveAction(String action) {
        return MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE.equals(action)
                || MemoryConstants.REVIEW_ACTION_APPROVE_RULE.equals(action)
                || MemoryConstants.REVIEW_ACTION_APPROVE_WORKFLOW.equals(action)
                || MemoryConstants.REVIEW_ACTION_APPROVE_DECISION.equals(action);
    }

    private boolean isRouteAction(String action) {
        return MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE.equals(action)
                || MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW.equals(action)
                || MemoryConstants.REVIEW_ACTION_ROUTE_TO_DECISION.equals(action);
    }

    private String resolveRouteTargetType(String recommendedAction, CaptureRouteSuggestion suggestion) {
        if (suggestion != null && StringUtils.isNotBlank(suggestion.getTargetKnowledgeType())) {
            return suggestion.getTargetKnowledgeType();
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE.equals(recommendedAction)) {
            return MemoryConstants.KNOWLEDGE_TYPE_RULE;
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW.equals(recommendedAction)) {
            return MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW;
        }
        return MemoryConstants.KNOWLEDGE_TYPE_DECISION;
    }

    private CaptureAiReviewEntity createRunningRecord(CaptureDraftEntity draftEntity, String traceId) {
        CaptureAiReviewEntity reviewEntity = new CaptureAiReviewEntity();
        reviewEntity.setWorkspaceId(draftEntity.getWorkspaceId());
        reviewEntity.setDraftId(draftEntity.getId());
        reviewEntity.setStatus(MemoryConstants.AI_REVIEW_STATUS_RUNNING);
        reviewEntity.setSimilarHit(0);
        reviewEntity.setExecuted(0);
        reviewEntity.setTraceId(traceId);
        captureAiReviewMapper.insert(reviewEntity);
        return reviewEntity;
    }

    private void finishEscalate(CaptureAiReviewEntity reviewEntity,
                                CaptureDraftEntity draftEntity,
                                List<CaptureSimilarKnowledgeHint> similarList,
                                CaptureRouteSuggestion suggestion,
                                String reason,
                                boolean similarHit,
                                long startMillis) {
        fillSuggestionFields(reviewEntity, suggestion, similarList);
        reviewEntity.setStatus(MemoryConstants.AI_REVIEW_STATUS_DONE);
        reviewEntity.setDecision(MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN);
        reviewEntity.setSimilarHit(similarHit ? 1 : 0);
        reviewEntity.setExecuted(0);
        reviewEntity.setErrorMessage(StringUtils.left(reason, 512));
        reviewEntity.setLatencyMs((int) (System.currentTimeMillis() - startMillis));
        captureAiReviewMapper.updateById(reviewEntity);
        syncDraftAiReviewFields(draftEntity, MemoryConstants.AI_REVIEW_STATUS_DONE,
                MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN);
        log.info("AI Review 转人工 draftId={} reason={} similarHit={}",
                draftEntity.getId(), reason, similarHit);
    }

    private void finishExecuted(CaptureAiReviewEntity reviewEntity,
                                CaptureDraftEntity draftEntity,
                                List<CaptureSimilarKnowledgeHint> similarList,
                                CaptureRouteSuggestion suggestion,
                                String decision,
                                boolean executed,
                                long startMillis) {
        fillSuggestionFields(reviewEntity, suggestion, similarList);
        reviewEntity.setStatus(MemoryConstants.AI_REVIEW_STATUS_DONE);
        reviewEntity.setDecision(decision);
        reviewEntity.setSimilarHit(0);
        reviewEntity.setExecuted(executed ? 1 : 0);
        reviewEntity.setLatencyMs((int) (System.currentTimeMillis() - startMillis));
        captureAiReviewMapper.updateById(reviewEntity);
        // 草稿可能已被 approve/reject 更新，重新加载后写冗余字段
        CaptureDraftEntity latestDraft = captureDraftMapper.selectById(draftEntity.getId());
        if (latestDraft != null) {
            syncDraftAiReviewFields(latestDraft, MemoryConstants.AI_REVIEW_STATUS_DONE, decision);
        }
        log.info("AI Review 自动执行完成 draftId={} decision={}", draftEntity.getId(), decision);
    }

    private void finishFailed(CaptureAiReviewEntity reviewEntity,
                              CaptureDraftEntity draftEntity,
                              String errorMessage,
                              long startMillis) {
        reviewEntity.setStatus(MemoryConstants.AI_REVIEW_STATUS_FAILED);
        reviewEntity.setDecision(MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN);
        reviewEntity.setExecuted(0);
        reviewEntity.setErrorMessage(StringUtils.left(
                StringUtils.defaultIfBlank(errorMessage, "AI 审查执行异常"), 512));
        reviewEntity.setLatencyMs((int) (System.currentTimeMillis() - startMillis));
        captureAiReviewMapper.updateById(reviewEntity);
        syncDraftAiReviewFields(draftEntity, MemoryConstants.AI_REVIEW_STATUS_FAILED,
                MemoryConstants.AI_REVIEW_DECISION_ESCALATE_HUMAN);
    }

    private void fillSuggestionFields(CaptureAiReviewEntity reviewEntity,
                                      CaptureRouteSuggestion suggestion,
                                      List<CaptureSimilarKnowledgeHint> similarList) {
        reviewEntity.setSimilarJson(MemoryJsonUtil.toJson(similarList));
        if (suggestion == null) {
            return;
        }
        reviewEntity.setConfidence(suggestion.getConfidence());
        reviewEntity.setRecommendedAction(suggestion.getRecommendedAction());
        reviewEntity.setRecommendedRejectReason(suggestion.getRecommendedRejectReason());
        reviewEntity.setChecklistJson(MemoryJsonUtil.toJson(suggestion.getChecklistHints()));
        reviewEntity.setLlmResultJson(MemoryJsonUtil.toJson(suggestion));
    }

    private void syncDraftAiReviewFields(CaptureDraftEntity draftEntity, Integer status, String decision) {
        if (draftEntity == null || draftEntity.getId() == null) {
            return;
        }
        CaptureDraftEntity updateEntity = new CaptureDraftEntity();
        updateEntity.setId(draftEntity.getId());
        updateEntity.setAiReviewStatus(status);
        updateEntity.setAiReviewDecision(decision);
        captureDraftMapper.updateById(updateEntity);
        draftEntity.setAiReviewStatus(status);
        draftEntity.setAiReviewDecision(decision);
    }

    /**
     * 自动执行后补充 review_comment（approve 路径未写入备注时）
     */
    private void markDraftReviewComment(Long draftId, String reviewComment) {
        CaptureDraftEntity updateEntity = new CaptureDraftEntity();
        updateEntity.setId(draftId);
        updateEntity.setReviewComment(reviewComment);
        captureDraftMapper.updateById(updateEntity);
    }

    private CaptureAiReviewEntity findLatestEntity(Long draftId, String workspaceId) {
        LambdaQueryWrapper<CaptureAiReviewEntity> queryWrapper = new LambdaQueryWrapper<CaptureAiReviewEntity>();
        queryWrapper.eq(CaptureAiReviewEntity::getDraftId, draftId)
                .eq(CaptureAiReviewEntity::getWorkspaceId, workspaceId)
                .orderByDesc(CaptureAiReviewEntity::getId)
                .last("LIMIT 1");
        return captureAiReviewMapper.selectOne(queryWrapper);
    }

    private CaptureAiReviewView toView(CaptureAiReviewEntity entity) {
        if (entity == null) {
            return null;
        }
        CaptureAiReviewView view = new CaptureAiReviewView();
        view.setId(entity.getId());
        view.setDraftId(entity.getDraftId());
        view.setStatus(entity.getStatus());
        view.setDecision(entity.getDecision());
        view.setSimilarHit(entity.getSimilarHit() != null && entity.getSimilarHit() == 1);
        view.setConfidence(entity.getConfidence());
        view.setRecommendedAction(entity.getRecommendedAction());
        view.setRecommendedRejectReason(entity.getRecommendedRejectReason());
        view.setExecuted(entity.getExecuted() != null && entity.getExecuted() == 1);
        view.setErrorMessage(entity.getErrorMessage());
        view.setLatencyMs(entity.getLatencyMs());
        view.setTraceId(entity.getTraceId());
        view.setCreateTime(entity.getCreateTime());
        view.setUpdateTime(entity.getUpdateTime());

        if (StringUtils.isNotBlank(entity.getSimilarJson())) {
            view.setSimilarKnowledge(cn.hutool.json.JSONUtil.toList(
                    entity.getSimilarJson(), CaptureSimilarKnowledgeHint.class));
        }
        if (StringUtils.isNotBlank(entity.getChecklistJson())) {
            view.setChecklistHints(cn.hutool.json.JSONUtil.toList(
                    entity.getChecklistJson(), CaptureRouteChecklistHint.class));
        }
        if (StringUtils.isNotBlank(entity.getLlmResultJson())) {
            view.setRouteSuggestion(cn.hutool.json.JSONUtil.toBean(
                    entity.getLlmResultJson(), CaptureRouteSuggestion.class));
        }
        return view;
    }
}
