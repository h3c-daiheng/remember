package com.zhiyi.memory.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.PageResult;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.domain.vo.UserProfileBrief;
import com.zhiyi.config.AiReviewProperties;
import com.zhiyi.memory.dao.CaptureAiReviewMapper;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.SystemEventMapper;
import com.zhiyi.memory.domain.CaptureAiReviewDetailView;
import com.zhiyi.memory.domain.CaptureAiReviewListItem;
import com.zhiyi.memory.domain.CaptureAiReviewView;
import com.zhiyi.memory.domain.CaptureDraftView;
import com.zhiyi.memory.domain.CaptureMergeRuleRequest;
import com.zhiyi.memory.domain.CaptureRejectRequest;
import com.zhiyi.memory.domain.CaptureRouteRequest;
import com.zhiyi.memory.domain.CaptureRouteResult;
import com.zhiyi.memory.domain.CaptureRouteSuggestion;
import com.zhiyi.memory.domain.CaptureSimilarKnowledgeHint;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.engine.AiReviewAsyncService;
import com.zhiyi.memory.engine.AiReviewEngine;
import com.zhiyi.memory.engine.CaptureRouteEngine;
import com.zhiyi.memory.engine.FactTransformEngine;
import com.zhiyi.memory.engine.SimilarKnowledgeFinder;
import com.zhiyi.memory.entity.CaptureAiReviewEntity;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.entity.SystemEventEntity;
import com.zhiyi.memory.service.AgentCreatorResolver;
import com.zhiyi.memory.util.MemoryJsonUtil;
import com.zhiyi.service.UserProfileService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Capture 草稿服务：列表查询、采纳、拒绝、路由与合并，按工作空间隔离
 */
@Service
public class CaptureService {

    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);

    private final CaptureDraftMapper captureDraftMapper;
    private final CaptureAiReviewMapper captureAiReviewMapper;
    private final SystemEventMapper systemEventMapper;
    private final KnowledgeService knowledgeService;
    private final CaptureRouteEngine captureRouteEngine;
    private final FactTransformEngine factTransformEngine;
    private final ContextNormalizer contextNormalizer;
    private final UserProfileService userProfileService;
    private final SimilarKnowledgeFinder similarKnowledgeFinder;
    private final AiReviewProperties aiReviewProperties;
    private final AiReviewEngine aiReviewEngine;
    private final AiReviewAsyncService aiReviewAsyncService;
    private final AgentCreatorResolver agentCreatorResolver;

    public CaptureService(CaptureDraftMapper captureDraftMapper,
                          CaptureAiReviewMapper captureAiReviewMapper,
                          SystemEventMapper systemEventMapper,
                          KnowledgeService knowledgeService,
                          CaptureRouteEngine captureRouteEngine,
                          FactTransformEngine factTransformEngine,
                          ContextNormalizer contextNormalizer,
                          UserProfileService userProfileService,
                          SimilarKnowledgeFinder similarKnowledgeFinder,
                          AiReviewProperties aiReviewProperties,
                          AiReviewEngine aiReviewEngine,
                          AiReviewAsyncService aiReviewAsyncService,
                          AgentCreatorResolver agentCreatorResolver) {
        this.captureDraftMapper = captureDraftMapper;
        this.captureAiReviewMapper = captureAiReviewMapper;
        this.systemEventMapper = systemEventMapper;
        this.knowledgeService = knowledgeService;
        this.captureRouteEngine = captureRouteEngine;
        this.factTransformEngine = factTransformEngine;
        this.contextNormalizer = contextNormalizer;
        this.userProfileService = userProfileService;
        this.similarKnowledgeFinder = similarKnowledgeFinder;
        this.aiReviewProperties = aiReviewProperties;
        this.aiReviewEngine = aiReviewEngine;
        this.aiReviewAsyncService = aiReviewAsyncService;
        this.agentCreatorResolver = agentCreatorResolver;
    }

    /**
     * 按关键词搜索 Capture 草稿（含各审核状态），供闭环追踪等场景选用
     */
    public List<CaptureDraftView> searchDrafts(String workspaceId, String keyword, int limit) {
        requireWorkspaceId(workspaceId);
        int pageSize = Math.min(Math.max(limit, 1), 50);
        LambdaQueryWrapper<CaptureDraftEntity> queryWrapper = new LambdaQueryWrapper<CaptureDraftEntity>();
        queryWrapper.eq(CaptureDraftEntity::getWorkspaceId, workspaceId);
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.like(CaptureDraftEntity::getDraftJson, keyword.trim());
        }
        queryWrapper.orderByDesc(CaptureDraftEntity::getCreateTime);
        queryWrapper.last("LIMIT " + pageSize);
        List<CaptureDraftEntity> entityList = captureDraftMapper.selectList(queryWrapper);
        return toViewList(entityList);
    }

    /**
     * 查询当前工作空间待确认的 Capture 草稿列表
     */
    public List<CaptureDraftView> listPendingDrafts(String workspaceId) {
        return listPendingDrafts(workspaceId, null);
    }

    /**
     * 查询待确认草稿；可按 AI Review 决策筛选（如 escalate_human）
     */
    public List<CaptureDraftView> listPendingDrafts(String workspaceId, String aiReviewDecision) {
        requireWorkspaceId(workspaceId);
        LambdaQueryWrapper<CaptureDraftEntity> queryWrapper = new LambdaQueryWrapper<CaptureDraftEntity>();
        queryWrapper.eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                .eq(CaptureDraftEntity::getReviewStatus, MemoryConstants.REVIEW_PENDING);
        if (StringUtils.isNotBlank(aiReviewDecision)) {
            queryWrapper.eq(CaptureDraftEntity::getAiReviewDecision, aiReviewDecision.trim());
        }
        queryWrapper.orderByDesc(CaptureDraftEntity::getCreateTime);
        List<CaptureDraftEntity> entityList = captureDraftMapper.selectList(queryWrapper);
        return toViewList(entityList);
    }

    /**
     * 查询草稿最新 AI Review 记录
     */
    public CaptureAiReviewView getAiReview(Long draftId, String workspaceId) {
        requireDraftInWorkspace(draftId, workspaceId);
        return aiReviewEngine.getLatestReview(draftId, workspaceId);
    }

    /**
     * 分页查询当前工作空间 AI Review 记录（全量历史，含已处理草稿）
     */
    public PageResult<CaptureAiReviewListItem> listAiReviews(int pageNum, int pageSize,
                                                             Integer status, String decision,
                                                             Boolean similarHit, String keyword,
                                                             String workspaceId) {
        requireWorkspaceId(workspaceId);
        int safePageNum = pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize < 1 ? 20 : Math.min(pageSize, 100);

        // 关键词先筛草稿，再按 draftId 过滤审查记录
        Set<Long> matchedDraftIdSet = null;
        if (StringUtils.isNotBlank(keyword)) {
            matchedDraftIdSet = findDraftIdsByKeyword(workspaceId, keyword.trim());
            if (matchedDraftIdSet.isEmpty()) {
                return PageResult.of(0, new ArrayList<CaptureAiReviewListItem>());
            }
        }

        PageHelper.startPage(safePageNum, safePageSize);
        LambdaQueryWrapper<CaptureAiReviewEntity> queryWrapper = new LambdaQueryWrapper<CaptureAiReviewEntity>();
        queryWrapper.eq(CaptureAiReviewEntity::getWorkspaceId, workspaceId);
        if (status != null) {
            queryWrapper.eq(CaptureAiReviewEntity::getStatus, status);
        }
        if (StringUtils.isNotBlank(decision)) {
            queryWrapper.eq(CaptureAiReviewEntity::getDecision, decision.trim());
        }
        if (similarHit != null) {
            queryWrapper.eq(CaptureAiReviewEntity::getSimilarHit, similarHit ? 1 : 0);
        }
        if (matchedDraftIdSet != null) {
            queryWrapper.in(CaptureAiReviewEntity::getDraftId, matchedDraftIdSet);
        }
        queryWrapper.orderByDesc(CaptureAiReviewEntity::getCreateTime)
                .orderByDesc(CaptureAiReviewEntity::getId);
        List<CaptureAiReviewEntity> entityList = captureAiReviewMapper.selectList(queryWrapper);
        PageInfo<CaptureAiReviewEntity> pageInfo = new PageInfo<CaptureAiReviewEntity>(entityList);
        return PageResult.of(pageInfo.getTotal(), toAiReviewListItems(entityList));
    }

    /**
     * 按记录主键查询 AI Review 详情（含草稿摘要）
     */
    public CaptureAiReviewDetailView getAiReviewById(Long reviewId, String workspaceId) {
        requireWorkspaceId(workspaceId);
        CaptureAiReviewView reviewView = aiReviewEngine.getReviewById(reviewId, workspaceId);
        if (reviewView == null) {
            throw new BusinessException(404, "AI 审查记录不存在");
        }
        CaptureAiReviewDetailView detailView = new CaptureAiReviewDetailView();
        detailView.setReview(reviewView);

        if (reviewView.getDraftId() != null) {
            CaptureDraftEntity draftEntity = captureDraftMapper.selectById(reviewView.getDraftId());
            if (draftEntity != null && workspaceId.equals(draftEntity.getWorkspaceId())) {
                KnowledgeDraftContent draftContent = MemoryJsonUtil.parseDraftContent(draftEntity.getDraftJson());
                detailView.setDraftTitle(draftContent == null ? null : draftContent.getTitle());
                detailView.setDraftReviewStatus(draftEntity.getReviewStatus());
                enrichDetailSubmitter(detailView, draftEntity.getEventId());
            }
        }
        return detailView;
    }

    /**
     * 按关键词在草稿 JSON 中模糊匹配，返回草稿 ID 集合
     */
    private Set<Long> findDraftIdsByKeyword(String workspaceId, String keyword) {
        LambdaQueryWrapper<CaptureDraftEntity> draftQuery = new LambdaQueryWrapper<CaptureDraftEntity>();
        draftQuery.eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                .like(CaptureDraftEntity::getDraftJson, keyword)
                .select(CaptureDraftEntity::getId);
        List<CaptureDraftEntity> draftEntityList = captureDraftMapper.selectList(draftQuery);
        Set<Long> draftIdSet = new HashSet<Long>();
        if (draftEntityList != null) {
            for (CaptureDraftEntity draftEntity : draftEntityList) {
                if (draftEntity != null && draftEntity.getId() != null) {
                    draftIdSet.add(draftEntity.getId());
                }
            }
        }
        return draftIdSet;
    }

    /**
     * 批量组装列表项，补全草稿标题与审核状态
     */
    private List<CaptureAiReviewListItem> toAiReviewListItems(List<CaptureAiReviewEntity> entityList) {
        List<CaptureAiReviewListItem> itemList = new ArrayList<CaptureAiReviewListItem>();
        if (entityList == null || entityList.isEmpty()) {
            return itemList;
        }
        Set<Long> draftIdSet = new HashSet<Long>();
        for (CaptureAiReviewEntity entity : entityList) {
            if (entity != null && entity.getDraftId() != null) {
                draftIdSet.add(entity.getDraftId());
            }
        }
        Map<Long, CaptureDraftEntity> draftMap = new HashMap<Long, CaptureDraftEntity>();
        if (!draftIdSet.isEmpty()) {
            List<CaptureDraftEntity> draftEntityList = captureDraftMapper.selectBatchIds(draftIdSet);
            if (draftEntityList != null) {
                for (CaptureDraftEntity draftEntity : draftEntityList) {
                    if (draftEntity != null && draftEntity.getId() != null) {
                        draftMap.put(draftEntity.getId(), draftEntity);
                    }
                }
            }
        }
        for (CaptureAiReviewEntity entity : entityList) {
            if (entity == null) {
                continue;
            }
            CaptureAiReviewListItem listItem = new CaptureAiReviewListItem();
            listItem.setId(entity.getId());
            listItem.setDraftId(entity.getDraftId());
            listItem.setStatus(entity.getStatus());
            listItem.setDecision(entity.getDecision());
            listItem.setSimilarHit(entity.getSimilarHit() != null && entity.getSimilarHit() == 1);
            listItem.setConfidence(entity.getConfidence());
            listItem.setExecuted(entity.getExecuted() != null && entity.getExecuted() == 1);
            listItem.setErrorMessage(entity.getErrorMessage());
            listItem.setLatencyMs(entity.getLatencyMs());
            listItem.setCreateTime(entity.getCreateTime());
            CaptureDraftEntity draftEntity = draftMap.get(entity.getDraftId());
            if (draftEntity != null) {
                KnowledgeDraftContent draftContent = MemoryJsonUtil.parseDraftContent(draftEntity.getDraftJson());
                listItem.setDraftTitle(draftContent == null ? null : draftContent.getTitle());
                listItem.setDraftReviewStatus(draftEntity.getReviewStatus());
            }
            itemList.add(listItem);
        }
        return itemList;
    }

    /**
     * 为详情补全提交人展示信息
     */
    private void enrichDetailSubmitter(CaptureAiReviewDetailView detailView, Long eventId) {
        if (detailView == null || eventId == null) {
            return;
        }
        SystemEventEntity eventEntity = systemEventMapper.selectById(eventId);
        if (eventEntity == null || eventEntity.getCreatorId() == null) {
            return;
        }
        detailView.setSubmitterId(eventEntity.getCreatorId());
        UserProfileBrief profileBrief = userProfileService.resolveOne(eventEntity.getCreatorId());
        if (profileBrief != null) {
            detailView.setSubmitterNickname(profileBrief.getNickname());
            detailView.setSubmitterAvatar(profileBrief.getAvatar());
        }
    }

    /**
     * 重跑 AI Review：标记排队后异步执行。
     *
     * @param reviewerId Web 端触发重跑的当前登录用户；自动执行采纳/路由时作为审核人与知识创建者
     */
    public CaptureAiReviewView retryAiReview(Long draftId, String workspaceId, Long reviewerId) {
        CaptureDraftEntity draftEntity = requireDraftInWorkspace(draftId, workspaceId);
        if (draftEntity.getReviewStatus() == null
                || draftEntity.getReviewStatus() != MemoryConstants.REVIEW_PENDING) {
            throw new BusinessException(400, "仅待确认草稿可重跑 AI 审查");
        }
        if (draftEntity.getAiReviewStatus() != null
                && draftEntity.getAiReviewStatus() == MemoryConstants.AI_REVIEW_STATUS_RUNNING) {
            throw new BusinessException(400, "AI 审查进行中，请稍后再试");
        }
        // 使用 UpdateWrapper：QUEUED=0 与 decision=null 都能可靠写入（updateById 会跳过 null，且部分策略对 0 不友好）
        LambdaUpdateWrapper<CaptureDraftEntity> updateWrapper = new LambdaUpdateWrapper<CaptureDraftEntity>();
        updateWrapper.eq(CaptureDraftEntity::getId, draftId)
                .set(CaptureDraftEntity::getAiReviewStatus, MemoryConstants.AI_REVIEW_STATUS_QUEUED)
                .set(CaptureDraftEntity::getAiReviewDecision, null);
        captureDraftMapper.update(null, updateWrapper);
        aiReviewAsyncService.reviewDraftAsync(draftId, reviewerId);
        return aiReviewEngine.getLatestReview(draftId, workspaceId);
    }

    /**
     * 查询草稿详情，并校验工作空间归属
     */
    public CaptureDraftView getDraft(Long draftId, String workspaceId) {
        CaptureDraftEntity entity = requireDraftInWorkspace(draftId, workspaceId);
        CaptureDraftView draftView = toView(entity);
        enrichSubmitterProfile(draftView, entity.getEventId());
        return draftView;
    }

    /**
     * 获取 Capture 草稿归类建议
     *
     * @param forceLlm true 时忽略置信度阈值，主动触发 LLM 增强
     */
    public CaptureRouteSuggestion getRouteSuggestion(Long draftId, String workspaceId, boolean forceLlm) {
        CaptureDraftView draftView = getDraft(draftId, workspaceId);
        return captureRouteEngine.suggest(draftView, workspaceId, forceLlm);
    }

    /**
     * 采纳草稿：按 Agent 提交时声明的类型确认并直接发布到对应知识层
     */
    @Transactional(rollbackFor = Exception.class)
    public CaptureRouteResult approveDraft(Long draftId, KnowledgeDraftContent editedContent,
                                           Long reviewerId, String workspaceId) {
        CaptureDraftEntity entity = requireDraftInWorkspace(draftId, workspaceId);
        if (entity.getReviewStatus() != MemoryConstants.REVIEW_PENDING) {
            throw new BusinessException(400, "草稿已处理");
        }
        KnowledgeDraftContent draftContent = prepareDraftContent(editedContent, entity.getDraftJson());
        String submittedType = resolveSubmittedKnowledgeType(draftContent);
        // AI Review 自动路径 reviewerId 为空，需回退到提交人作为知识创建者
        Long knowledgeCreatorId = resolveKnowledgeCreatorId(reviewerId, entity);
        log.info("开始采纳草稿 draftId={} eventId={} reviewerId={} knowledgeCreatorId={} workspaceId={} submittedType={}",
                draftId, entity.getEventId(), reviewerId, knowledgeCreatorId, workspaceId, submittedType);

        CaptureRouteResult result = new CaptureRouteResult();
        result.setCaptureDraftId(draftId);
        // 人工发布前相似告警（不阻断）；AI 自动路径 reviewerId 为空时跳过告警填充
        if (reviewerId != null) {
            result.setSimilarWarnings(collectSimilarWarnings(draftContent, workspaceId));
        }

        if (MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(submittedType)) {
            Long knowledgeId = knowledgeService.createFromDraft(
                    draftContent, knowledgeCreatorId, workspaceId,
                    MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE, true, draftId);
            log.info("草稿采纳成功 draftId={} knowledgeId={} type=experience", draftId, knowledgeId);
            entity.setReviewStatus(MemoryConstants.REVIEW_APPROVED);
            entity.setKnowledgeId(knowledgeId);
            entity.setReviewerId(reviewerId);
            entity.setReviewAction(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE);
            entity.setDraftJson(MemoryJsonUtil.toJson(draftContent));
            captureDraftMapper.updateById(entity);

            result.setKnowledgeId(knowledgeId);
            result.setKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
            result.setLifecycleStatus(MemoryConstants.LIFECYCLE_PUBLISHED);
            result.setRedirectPath(buildPublishedKnowledgeRedirectPath(submittedType, knowledgeId));
            return result;
        }

        Long knowledgeId = confirmStructuredDraft(entity, draftContent, reviewerId, knowledgeCreatorId, submittedType);
        result.setKnowledgeId(knowledgeId);
        result.setKnowledgeType(submittedType);
        result.setLifecycleStatus(MemoryConstants.LIFECYCLE_PUBLISHED);
        result.setRedirectPath(buildPublishedKnowledgeRedirectPath(submittedType, knowledgeId));
        return result;
    }

    /**
     * 确认 Rule / Workflow / Decision 类型草稿，直接发布到对应知识层
     *
     * @param reviewerId         人工审核人，AI 自动路径可为 null
     * @param knowledgeCreatorId 知识创建者（人工路径同审核人；AI 路径回退到提交人）
     */
    private Long confirmStructuredDraft(CaptureDraftEntity entity, KnowledgeDraftContent draftContent,
                                        Long reviewerId, Long knowledgeCreatorId, String targetType) {
        KnowledgeDraftContent transformedContent = factTransformEngine.transform(draftContent, targetType);
        Long knowledgeId = knowledgeService.createFromDraft(
                transformedContent, knowledgeCreatorId, entity.getWorkspaceId(), targetType, true, entity.getId());

        entity.setReviewStatus(MemoryConstants.REVIEW_APPROVED);
        entity.setReviewerId(reviewerId);
        entity.setRoutedKnowledgeId(knowledgeId);
        entity.setReviewAction(resolveApproveAction(targetType));
        entity.setDraftJson(MemoryJsonUtil.toJson(transformedContent));
        captureDraftMapper.updateById(entity);
        log.info("结构化知识发布成功 draftId={} knowledgeId={} type={}", entity.getId(), knowledgeId, targetType);
        return knowledgeId;
    }

    /**
     * 解析 Agent 提交时声明的知识类型，缺省为 experience
     */
    private String resolveSubmittedKnowledgeType(KnowledgeDraftContent draftContent) {
        if (draftContent == null || StringUtils.isBlank(draftContent.getSubmittedKnowledgeType())) {
            return MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE;
        }
        return draftContent.getSubmittedKnowledgeType().trim();
    }

    /**
     * 构建已发布知识的详情页跳转路径
     */
    private String buildPublishedKnowledgeRedirectPath(String targetType, Long knowledgeId) {
        if (MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(targetType)) {
            return "/experience/" + knowledgeId;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(targetType)) {
            return "/rule/" + knowledgeId;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(targetType)) {
            return "/rule/" + knowledgeId + "?type=workflow";
        }
        return "/decision/" + knowledgeId;
    }

    /**
     * 将 knowledgeType 映射为 Review 动作
     */
    private String resolveApproveAction(String targetType) {
        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(targetType)) {
            return MemoryConstants.REVIEW_ACTION_APPROVE_RULE;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(targetType)) {
            return MemoryConstants.REVIEW_ACTION_APPROVE_WORKFLOW;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(targetType)) {
            return MemoryConstants.REVIEW_ACTION_APPROVE_DECISION;
        }
        return MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE;
    }

    /**
     * 拒绝草稿，写入拒绝码与备注
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectDraft(Long draftId, CaptureRejectRequest rejectRequest, Long reviewerId, String workspaceId) {
        CaptureDraftEntity entity = requireDraftInWorkspace(draftId, workspaceId);
        if (entity.getReviewStatus() != MemoryConstants.REVIEW_PENDING) {
            throw new BusinessException(400, "草稿已处理");
        }
        entity.setReviewStatus(MemoryConstants.REVIEW_REJECTED);
        entity.setReviewerId(reviewerId);
        entity.setReviewAction(MemoryConstants.REVIEW_ACTION_REJECT);
        entity.setRejectReason(rejectRequest.getRejectReason());
        entity.setReviewComment(rejectRequest.getReviewComment());
        captureDraftMapper.updateById(entity);
    }

    /**
     * 路由草稿为 Rule / Workflow / Decision 并直接发布
     */
    @Transactional(rollbackFor = Exception.class)
    public CaptureRouteResult routeDraft(Long draftId, CaptureRouteRequest routeRequest,
                                         Long reviewerId, String workspaceId) {
        CaptureDraftEntity entity = requireDraftInWorkspace(draftId, workspaceId);
        if (entity.getReviewStatus() != MemoryConstants.REVIEW_PENDING) {
            throw new BusinessException(400, "草稿已处理");
        }
        String targetType = routeRequest.getTargetType();
        if (!MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(targetType)
                && !MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(targetType)
                && !MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(targetType)) {
            throw new BusinessException(400, "targetType 仅支持 rule、workflow 或 decision");
        }
        KnowledgeDraftContent sourceContent = prepareDraftContent(routeRequest.getEditedContent(), entity.getDraftJson());
        KnowledgeDraftContent transformedContent = factTransformEngine.transform(sourceContent, targetType);
        // AI Review 自动路由时 reviewerId 为空，回退到提交人作为知识创建者
        Long knowledgeCreatorId = resolveKnowledgeCreatorId(reviewerId, entity);
        Long knowledgeId = knowledgeService.createFromDraft(
                transformedContent, knowledgeCreatorId, workspaceId, targetType, true, draftId);

        entity.setReviewStatus(MemoryConstants.REVIEW_REJECTED);
        entity.setReviewerId(reviewerId);
        entity.setRoutedKnowledgeId(knowledgeId);
        entity.setRejectReason(routeRequest.getRejectReason());
        entity.setReviewComment(routeRequest.getReviewComment());
        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(targetType)) {
            entity.setReviewAction(MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE);
        } else if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(targetType)) {
            entity.setReviewAction(MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW);
        } else {
            entity.setReviewAction(MemoryConstants.REVIEW_ACTION_ROUTE_TO_DECISION);
        }
        captureDraftMapper.updateById(entity);

        CaptureRouteResult result = new CaptureRouteResult();
        result.setKnowledgeId(knowledgeId);
        result.setKnowledgeType(targetType);
        result.setLifecycleStatus(MemoryConstants.LIFECYCLE_PUBLISHED);
        result.setCaptureDraftId(draftId);
        result.setRedirectPath(buildPublishedKnowledgeRedirectPath(targetType, knowledgeId));
        if (reviewerId != null) {
            result.setSimilarWarnings(collectSimilarWarnings(sourceContent, workspaceId));
        }
        log.info("草稿路由发布成功 draftId={} knowledgeId={} targetType={}", draftId, knowledgeId, targetType);
        return result;
    }

    /**
     * 将 Capture 草稿中的 rule/constraint Fact 合并到已有 Rule
     */
    @Transactional(rollbackFor = Exception.class)
    public CaptureRouteResult mergeIntoRule(Long draftId, CaptureMergeRuleRequest mergeRequest,
                                            Long reviewerId, String workspaceId) {
        CaptureDraftEntity entity = requireDraftInWorkspace(draftId, workspaceId);
        if (entity.getReviewStatus() != MemoryConstants.REVIEW_PENDING) {
            throw new BusinessException(400, "草稿已处理");
        }
        if (mergeRequest.getTargetKnowledgeId() == null) {
            throw new BusinessException(400, "缺少目标 Rule ID");
        }
        KnowledgeDraftContent sourceContent = prepareDraftContent(mergeRequest.getEditedContent(), entity.getDraftJson());
        List<FactBlock> ruleFacts = factTransformEngine.extractRuleFacts(sourceContent);
        if (ruleFacts.isEmpty()) {
            throw new BusinessException(400, "草稿中无 rule/constraint 内容可合并");
        }
        knowledgeService.appendFacts(mergeRequest.getTargetKnowledgeId(), ruleFacts, workspaceId);

        entity.setReviewStatus(MemoryConstants.REVIEW_REJECTED);
        entity.setReviewerId(reviewerId);
        entity.setRoutedKnowledgeId(mergeRequest.getTargetKnowledgeId());
        entity.setReviewAction(MemoryConstants.REVIEW_ACTION_MERGE_TO_RULE);
        entity.setRejectReason(mergeRequest.getRejectReason());
        entity.setReviewComment(mergeRequest.getReviewComment());
        captureDraftMapper.updateById(entity);

        CaptureRouteResult result = new CaptureRouteResult();
        result.setKnowledgeId(mergeRequest.getTargetKnowledgeId());
        result.setKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_RULE);
        result.setCaptureDraftId(draftId);
        result.setRedirectPath("/rule/" + mergeRequest.getTargetKnowledgeId());
        return result;
    }

    private void requireWorkspaceId(String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
    }

    /**
     * 解析发布知识时的创建者：优先人工审核人；AI 自动路径无审核人时，
     * 回退到 system_event.creator_id（submit 时由 api_key 解析写入），再回退工作空间成员。
     */
    private Long resolveKnowledgeCreatorId(Long reviewerId, CaptureDraftEntity draftEntity) {
        if (reviewerId != null) {
            return reviewerId;
        }
        if (draftEntity != null && draftEntity.getEventId() != null) {
            SystemEventEntity eventEntity = systemEventMapper.selectById(draftEntity.getEventId());
            if (eventEntity != null && eventEntity.getCreatorId() != null) {
                return eventEntity.getCreatorId();
            }
        }
        String workspaceId = draftEntity == null ? null : draftEntity.getWorkspaceId();
        return agentCreatorResolver.resolveWorkspaceCreatorId(workspaceId);
    }

    /**
     * 清洗并规范化草稿上下文字段，避免入库时 project/module/repository 写法不一致
     */
    private KnowledgeDraftContent prepareDraftContent(KnowledgeDraftContent editedContent, String fallbackDraftJson) {
        KnowledgeDraftContent draftContent = editedContent != null
                ? MemoryJsonUtil.sanitizeDraftContent(editedContent)
                : MemoryJsonUtil.parseDraftContent(fallbackDraftJson);
        contextNormalizer.normalizeDraftContent(draftContent);
        return draftContent;
    }

    private CaptureDraftEntity requireDraftInWorkspace(Long draftId, String workspaceId) {
        requireWorkspaceId(workspaceId);
        CaptureDraftEntity entity = captureDraftMapper.selectById(draftId);
        if (entity == null) {
            throw new BusinessException(404, "草稿不存在");
        }
        if (!workspaceId.equals(entity.getWorkspaceId())) {
            throw new BusinessException(403, "无权访问该草稿");
        }
        return entity;
    }

    private CaptureDraftView toView(CaptureDraftEntity entity) {
        CaptureDraftView view = new CaptureDraftView();
        view.setId(entity.getId());
        view.setEventId(entity.getEventId());
        view.setDraftContent(MemoryJsonUtil.parseDraftContent(entity.getDraftJson()));
        view.setReviewStatus(entity.getReviewStatus());
        view.setKnowledgeId(entity.getKnowledgeId());
        view.setRejectReason(entity.getRejectReason());
        view.setReviewAction(entity.getReviewAction());
        view.setRoutedKnowledgeId(entity.getRoutedKnowledgeId());
        view.setReviewComment(entity.getReviewComment());
        view.setAiReviewStatus(entity.getAiReviewStatus());
        view.setAiReviewDecision(entity.getAiReviewDecision());
        view.setCreateTime(entity.getCreateTime());
        return view;
    }

    /**
     * 人工发布前收集达到门禁阈值的相似记忆，供前端强提示
     */
    private List<CaptureSimilarKnowledgeHint> collectSimilarWarnings(KnowledgeDraftContent draftContent,
                                                                     String workspaceId) {
        List<CaptureSimilarKnowledgeHint> similarList = similarKnowledgeFinder.findSimilar(
                draftContent, workspaceId);
        return similarKnowledgeFinder.filterAboveThreshold(
                similarList, aiReviewProperties.getSimilarThreshold());
    }

    /**
     * 批量转换草稿实体并补全提交人昵称与头像
     */
    private List<CaptureDraftView> toViewList(List<CaptureDraftEntity> entityList) {
        List<CaptureDraftView> viewList = new ArrayList<CaptureDraftView>();
        if (entityList == null || entityList.isEmpty()) {
            return viewList;
        }
        for (CaptureDraftEntity entity : entityList) {
            viewList.add(toView(entity));
        }
        enrichSubmitterProfiles(viewList, entityList);
        return viewList;
    }

    /**
     * 为单条草稿补全提交人信息（关联 system_event.creator_id）
     */
    private void enrichSubmitterProfile(CaptureDraftView draftView, Long eventId) {
        if (draftView == null || eventId == null) {
            return;
        }
        SystemEventEntity eventEntity = systemEventMapper.selectById(eventId);
        if (eventEntity == null || eventEntity.getCreatorId() == null) {
            return;
        }
        UserProfileBrief profileBrief = userProfileService.resolveOne(eventEntity.getCreatorId());
        applySubmitterProfile(draftView, eventEntity.getCreatorId(), profileBrief);
    }

    /**
     * 为草稿列表批量补全提交人信息，避免 N+1 查询
     */
    private void enrichSubmitterProfiles(List<CaptureDraftView> viewList, List<CaptureDraftEntity> entityList) {
        if (viewList == null || viewList.isEmpty() || entityList == null || entityList.isEmpty()) {
            return;
        }
        Set<Long> eventIdSet = new HashSet<Long>();
        for (CaptureDraftEntity entity : entityList) {
            if (entity != null && entity.getEventId() != null) {
                eventIdSet.add(entity.getEventId());
            }
        }
        if (eventIdSet.isEmpty()) {
            return;
        }
        List<SystemEventEntity> eventEntityList = systemEventMapper.selectBatchIds(eventIdSet);
        Map<Long, Long> eventCreatorMap = new HashMap<Long, Long>();
        Set<Long> submitterIdSet = new HashSet<Long>();
        if (eventEntityList != null) {
            for (SystemEventEntity eventEntity : eventEntityList) {
                if (eventEntity == null || eventEntity.getId() == null || eventEntity.getCreatorId() == null) {
                    continue;
                }
                eventCreatorMap.put(eventEntity.getId(), eventEntity.getCreatorId());
                submitterIdSet.add(eventEntity.getCreatorId());
            }
        }
        if (submitterIdSet.isEmpty()) {
            return;
        }
        Map<Long, UserProfileBrief> profileMap = userProfileService.batchResolve(submitterIdSet);
        int size = Math.min(viewList.size(), entityList.size());
        for (int index = 0; index < size; index++) {
            CaptureDraftView draftView = viewList.get(index);
            CaptureDraftEntity entity = entityList.get(index);
            if (draftView == null || entity == null || entity.getEventId() == null) {
                continue;
            }
            Long submitterId = eventCreatorMap.get(entity.getEventId());
            if (submitterId == null) {
                continue;
            }
            applySubmitterProfile(draftView, submitterId, profileMap.get(submitterId));
        }
    }

    /**
     * 将用户展示摘要写入草稿视图
     */
    private void applySubmitterProfile(CaptureDraftView draftView, Long submitterId, UserProfileBrief profileBrief) {
        if (draftView == null || submitterId == null || profileBrief == null) {
            return;
        }
        draftView.setSubmitterId(submitterId);
        draftView.setSubmitterNickname(profileBrief.getNickname());
        draftView.setSubmitterAvatar(profileBrief.getAvatar());
    }
}
