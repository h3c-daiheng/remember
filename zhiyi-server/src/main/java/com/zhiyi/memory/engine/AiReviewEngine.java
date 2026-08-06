package com.zhiyi.memory.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.config.AiReviewProperties;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.CaptureAiReviewMapper;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.domain.CaptureAiReviewView;
import com.zhiyi.memory.domain.CaptureDraftView;
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

import java.util.List;
import java.util.UUID;

/**
 * Capture AI Review 引擎：LLM 可用必调评审，相似交叉验证冲突或评审不通过转人工，否则自动执行
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

            // 极高相似重复门禁：无论 LLM 结论如何都转人工
            if (similarKnowledgeFinder.hasSimilarHit(
                    similarKnowledgeList, aiReviewProperties.getSimilarDuplicateThreshold())) {
                finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, null,
                        "极高相似已发布记忆，疑似重复，转人工处理（相似："
                                + summarizeSimilar(similarKnowledgeList) + "）", true, startMillis);
                return;
            }

            CaptureDraftView draftView = new CaptureDraftView();
            draftView.setId(draftEntity.getId());
            draftView.setEventId(draftEntity.getEventId());
            draftView.setDraftContent(draftContent);
            draftView.setReviewStatus(draftEntity.getReviewStatus());

            // LLM 可用则必调（forceLlm=true），规则引擎作基线；LLM 不可用时回退规则
            CaptureRouteSuggestion suggestion = captureRouteEngine.suggest(
                    draftView, draftEntity.getWorkspaceId(), true);
            if (suggestion.getSimilarKnowledge() == null || suggestion.getSimilarKnowledge().isEmpty()) {
                suggestion.setSimilarKnowledge(similarKnowledgeList);
            }

            // 二次门禁：suggest 阶段检索到的极高相似也拦截
            if (similarKnowledgeFinder.hasSimilarHit(
                    suggestion.getSimilarKnowledge(), aiReviewProperties.getSimilarDuplicateThreshold())) {
                finishEscalate(reviewEntity, draftEntity, suggestion.getSimilarKnowledge(), suggestion,
                        "路由建议阶段极高相似，疑似重复，转人工处理（相似："
                                + summarizeSimilar(suggestion.getSimilarKnowledge()) + "）", true, startMillis);
                return;
            }

            resolveDecision(reviewEntity, draftEntity, suggestion,
                    suggestion.getSimilarKnowledge(), startMillis, reviewerId);
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
     * 统一裁决：LLM 可用则必调，规则作基线；冲突或评审不通过转人工，否则自动执行
     *
     * <p>转人工条件：建议 reject、置信度不足、LLM 与规则结论不一致、相似交叉验证冲突
     * （approve 但高相似疑似重复 / 结论类型与相似记忆矛盾）。
     * LLM 不可用回退规则时，高置信度非 reject 仍自动执行。
     * 转人工 reason 带具体上下文（动作/置信度/理由/相似记忆），供人工定位。</p>
     */
    private void resolveDecision(CaptureAiReviewEntity reviewEntity,
                                 CaptureDraftEntity draftEntity,
                                 CaptureRouteSuggestion suggestion,
                                 List<CaptureSimilarKnowledgeHint> similarKnowledgeList,
                                 long startMillis,
                                 Long reviewerId) {
        String recommendedAction = suggestion == null ? null : suggestion.getRecommendedAction();
        double confidence = suggestion == null ? 0D : suggestion.getConfidence();
        String suggestionSource = suggestion == null ? null : suggestion.getSuggestionSource();
        boolean llmInvoked = "llm".equals(suggestionSource) || "hybrid".equals(suggestionSource);
        String reviewer = llmInvoked ? "LLM" : "规则";

        if (StringUtils.isBlank(recommendedAction)) {
            finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    "缺少推荐动作，转人工处理", false, startMillis);
            return;
        }
        if (MemoryConstants.REVIEW_ACTION_MERGE_TO_RULE.equals(recommendedAction)) {
            finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    "建议合并到已有 Rule，转人工处理（" + summarizeReasons(suggestion.getReasons()) + "）",
                    false, startMillis);
            return;
        }
        // 建议拒绝：转人工确认（不自动拒绝），带拒绝码与理由
        if (MemoryConstants.REVIEW_ACTION_REJECT.equals(recommendedAction)) {
            String rejectCode = StringUtils.defaultIfBlank(suggestion.getRecommendedRejectReason(), "未给出");
            finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    reviewer + "建议拒绝，转人工确认（拒绝码：" + rejectCode
                            + "；理由：" + summarizeReasons(suggestion.getReasons()) + "）",
                    false, startMillis);
            return;
        }
        // LLM 与规则结论不一致：转人工复核，带 LLM 建议与置信度（reasons 含规则基线动作）
        if (llmInvoked && "llm".equals(suggestionSource)) {
            finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    "LLM 与规则结论不一致，转人工复核（LLM 建议：" + actionLabel(recommendedAction)
                            + "，置信度 " + Math.round(confidence * 100) + "%；理由："
                            + summarizeReasons(suggestion.getReasons()) + "）",
                    false, startMillis);
            return;
        }
        // 置信度不足：转人工，带建议动作与置信度差值
        if (confidence < aiReviewProperties.getAutoExecuteConfidenceThreshold()) {
            finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    "置信度不足，转人工处理（建议：" + actionLabel(recommendedAction) + "，置信度 "
                            + Math.round(confidence * 100) + "% < 阈值 "
                            + Math.round(aiReviewProperties.getAutoExecuteConfidenceThreshold() * 100)
                            + "%；理由：" + summarizeReasons(suggestion.getReasons()) + "）",
                    false, startMillis);
            return;
        }
        // 相似交叉验证：approve 但存在高相似已发布记忆（疑似重复）-> 转人工，带相似记忆
        if (isApproveAction(recommendedAction)
                && similarKnowledgeFinder.hasSimilarHit(
                        similarKnowledgeList, aiReviewProperties.getSimilarThreshold())) {
            finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    "建议发布但存在高相似已发布记忆，疑似重复，转人工处理（建议："
                            + actionLabel(recommendedAction) + "；相似："
                            + summarizeSimilar(similarKnowledgeList) + "）",
                    true, startMillis);
            return;
        }
        // 相似交叉验证：结论类型与高相似记忆矛盾 -> 转人工，带类型对比
        if (hasSimilarTypeConflict(suggestion, similarKnowledgeList)) {
            finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    "结论类型与相似记忆矛盾，转人工处理（建议类型："
                            + StringUtils.defaultString(suggestion.getTargetKnowledgeType())
                            + "；相似：" + summarizeSimilar(similarKnowledgeList) + "）",
                    false, startMillis);
            return;
        }

        // 通过：自动执行 approve / route
        if (isApproveAction(recommendedAction)) {
            captureService.approveDraft(draftEntity.getId(), null, reviewerId, draftEntity.getWorkspaceId());
            markDraftReviewComment(draftEntity.getId(),
                    MemoryConstants.AI_REVIEW_COMMENT_PREFIX + recommendedAction);
            finishExecuted(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    MemoryConstants.AI_REVIEW_DECISION_APPROVE, true, startMillis);
            return;
        }

        if (isRouteAction(recommendedAction)) {
            if (!aiReviewProperties.isAutoRouteEnabled()) {
                finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                        "建议路由但自动路由未启用（" + actionLabel(recommendedAction) + "），转人工处理",
                        false, startMillis);
                return;
            }
            CaptureRouteRequest routeRequest = new CaptureRouteRequest();
            routeRequest.setTargetType(resolveRouteTargetType(recommendedAction, suggestion));
            routeRequest.setRejectReason(suggestion.getRecommendedRejectReason());
            routeRequest.setReviewComment(MemoryConstants.AI_REVIEW_COMMENT_PREFIX + recommendedAction);
            captureService.routeDraft(draftEntity.getId(), routeRequest, reviewerId, draftEntity.getWorkspaceId());
            finishExecuted(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                    MemoryConstants.AI_REVIEW_DECISION_ROUTE, true, startMillis);
            return;
        }

        finishEscalate(reviewEntity, draftEntity, similarKnowledgeList, suggestion,
                "未识别的推荐动作：" + recommendedAction + "，转人工处理", false, startMillis);
    }

    /**
     * 相似交叉验证：高相似记忆中存在与结论目标类型不同的，视为类型矛盾
     */
    private boolean hasSimilarTypeConflict(CaptureRouteSuggestion suggestion,
                                           List<CaptureSimilarKnowledgeHint> similarKnowledgeList) {
        if (suggestion == null || StringUtils.isBlank(suggestion.getTargetKnowledgeType())
                || similarKnowledgeList == null || similarKnowledgeList.isEmpty()) {
            return false;
        }
        String targetType = suggestion.getTargetKnowledgeType();
        for (CaptureSimilarKnowledgeHint hint : similarKnowledgeList) {
            if (hint == null
                    || hint.getSimilarityScore() < aiReviewProperties.getSimilarThreshold()) {
                continue;
            }
            if (!targetType.equals(hint.getKnowledgeType())) {
                return true;
            }
        }
        return false;
    }

    /** 推荐动作中文标签，供转人工 reason 直观展示 */
    private String actionLabel(String action) {
        if (action == null) {
            return "未知";
        }
        if (MemoryConstants.REVIEW_ACTION_REJECT.equals(action)) {
            return "拒绝";
        }
        if (MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE.equals(action)) {
            return "发布为经验";
        }
        if (MemoryConstants.REVIEW_ACTION_APPROVE_RULE.equals(action)) {
            return "发布为规则";
        }
        if (MemoryConstants.REVIEW_ACTION_APPROVE_WORKFLOW.equals(action)) {
            return "发布为流程";
        }
        if (MemoryConstants.REVIEW_ACTION_APPROVE_DECISION.equals(action)) {
            return "发布为决策";
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE.equals(action)) {
            return "路由为规则";
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW.equals(action)) {
            return "路由为流程";
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_DECISION.equals(action)) {
            return "路由为决策";
        }
        if (MemoryConstants.REVIEW_ACTION_MERGE_TO_RULE.equals(action)) {
            return "合并到规则";
        }
        return action;
    }

    /** 拼接 LLM/规则理由摘要（限长，避免 errorMessage 超 512） */
    private String summarizeReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "";
        }
        return StringUtils.left(String.join("；", reasons), 240);
    }

    /** 相似记忆摘要：#id《标题》(类型,相似度)，取前 2 条 */
    private String summarizeSimilar(List<CaptureSimilarKnowledgeHint> similarList) {
        if (similarList == null || similarList.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (CaptureSimilarKnowledgeHint hint : similarList) {
            if (hint == null) {
                continue;
            }
            if (count++ > 0) {
                sb.append("，");
            }
            sb.append("#").append(hint.getKnowledgeId())
                    .append("《").append(StringUtils.left(hint.getTitle(), 20)).append("》")
                    .append("(").append(StringUtils.defaultString(hint.getKnowledgeType()))
                    .append(",").append(Math.round(hint.getSimilarityScore() * 100)).append("%)");
            if (count >= 2) {
                break;
            }
        }
        return sb.toString();
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
