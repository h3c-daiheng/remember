package com.zhiyi.memory.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.PageResult;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.domain.vo.KnowledgeDeprecateResultVO;
import com.zhiyi.domain.vo.KnowledgeFeedbackSummaryVO;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.KnowledgeBatchDeleteResult;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.domain.KnowledgeImportResult;
import com.zhiyi.memory.domain.KnowledgeSaveRequest;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.KnowledgeFactEntity;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.graph.CascadeValidationService;
import com.zhiyi.memory.graph.RelationAsyncService;
import com.zhiyi.memory.graph.RelationEngine;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.memory.retrieval.RetrievalEngine;
import org.springframework.context.annotation.Lazy;
import com.zhiyi.domain.vo.UserProfileBrief;
import com.zhiyi.memory.util.KnowledgeMarkdownParser;
import com.zhiyi.memory.util.KnowledgeMarkdownSerializer;
import com.zhiyi.memory.util.MemoryJsonUtil;
import com.zhiyi.service.UserProfileService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识聚合服务：Knowledge 的 CRUD、发布与索引，按工作空间隔离
 */
@Service
public class KnowledgeService {

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeFactMapper knowledgeFactMapper;
    private final KnowledgeArtifactMapper knowledgeArtifactMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final MemoryFeedbackMapper memoryFeedbackMapper;
    private final RetrievalEngine retrievalEngine;
    private final ContextNormalizer contextNormalizer;
    private final RelationAsyncService relationAsyncService;
    private final RelationEngine relationEngine;
    private final KnowledgeTimelineService knowledgeTimelineService;
    private final CascadeValidationService cascadeValidationService;
    private final UserProfileService userProfileService;

    public KnowledgeService(KnowledgeMapper knowledgeMapper,
                            KnowledgeFactMapper knowledgeFactMapper,
                            KnowledgeArtifactMapper knowledgeArtifactMapper,
                            KnowledgeTagMapper knowledgeTagMapper,
                            MemoryFeedbackMapper memoryFeedbackMapper,
                            RetrievalEngine retrievalEngine,
                            ContextNormalizer contextNormalizer,
                            @Lazy RelationAsyncService relationAsyncService,
                            RelationEngine relationEngine,
                            KnowledgeTimelineService knowledgeTimelineService,
                            CascadeValidationService cascadeValidationService,
                            UserProfileService userProfileService) {
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeFactMapper = knowledgeFactMapper;
        this.knowledgeArtifactMapper = knowledgeArtifactMapper;
        this.knowledgeTagMapper = knowledgeTagMapper;
        this.memoryFeedbackMapper = memoryFeedbackMapper;
        this.retrievalEngine = retrievalEngine;
        this.contextNormalizer = contextNormalizer;
        this.relationAsyncService = relationAsyncService;
        this.relationEngine = relationEngine;
        this.knowledgeTimelineService = knowledgeTimelineService;
        this.cascadeValidationService = cascadeValidationService;
        this.userProfileService = userProfileService;
    }

    /**
     * 分页查询当前工作空间内知识列表，支持按类型与生命周期筛选
     */
    public PageResult<KnowledgeAggregate> list(int pageNum, int pageSize, String keyword,
                                               String knowledgeType, Integer lifecycleStatus,
                                               String workspaceId) {
        requireWorkspaceId(workspaceId);
        PageHelper.startPage(pageNum, pageSize);
        LambdaQueryWrapper<KnowledgeEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        queryWrapper.eq(KnowledgeEntity::getWorkspaceId, workspaceId);
        // knowledgeType 为空时默认仅 experience；传 all 时返回全部类型（记忆中心合并列表）
        if (StringUtils.isNotBlank(knowledgeType) && !"all".equals(knowledgeType)) {
            queryWrapper.eq(KnowledgeEntity::getKnowledgeType, knowledgeType);
        } else if (StringUtils.isBlank(knowledgeType)) {
            queryWrapper.eq(KnowledgeEntity::getKnowledgeType, MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
        }
        if (lifecycleStatus != null) {
            queryWrapper.eq(KnowledgeEntity::getLifecycleStatus, lifecycleStatus);
        } else {
            queryWrapper.eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED);
        }
        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper.like(KnowledgeEntity::getTitle, keyword)
                    .or().like(KnowledgeEntity::getProject, keyword)
                    .or().like(KnowledgeEntity::getModule, keyword));
        }
        queryWrapper.orderByDesc(KnowledgeEntity::getUpdateTime);
        List<KnowledgeEntity> entityList = knowledgeMapper.selectList(queryWrapper);
        PageInfo<KnowledgeEntity> pageInfo = new PageInfo<KnowledgeEntity>(entityList);
        List<KnowledgeAggregate> aggregateList = new ArrayList<KnowledgeAggregate>();
        for (KnowledgeEntity entity : entityList) {
            aggregateList.add(toAggregateSummary(entity));
        }
        enrichCreatorProfiles(aggregateList);
        return PageResult.of(pageInfo.getTotal(), aggregateList);
    }

    /**
     * 导出当前工作空间已发布知识为 Markdown 字符串
     *
     * @param workspaceId   工作空间主键
     * @param workspaceName 工作空间显示名(用于文件头)
     * @param knowledgeType knowledgeType,null/"all" 表示全部四种类型
     * @param exportTime    导出时间字符串(用于文件头)
     */
    public String exportMarkdown(String workspaceId, String workspaceName,
                                 String knowledgeType, String exportTime) {
        requireWorkspaceId(workspaceId);
        LambdaQueryWrapper<KnowledgeEntity> wrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        wrapper.eq(KnowledgeEntity::getWorkspaceId, workspaceId);
        wrapper.eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED);
        if (StringUtils.isNotBlank(knowledgeType) && !"all".equals(knowledgeType)) {
            wrapper.eq(KnowledgeEntity::getKnowledgeType, knowledgeType);
        }
        wrapper.orderByDesc(KnowledgeEntity::getUpdateTime);
        List<KnowledgeEntity> entities = knowledgeMapper.selectList(wrapper);
        List<KnowledgeAggregate> aggregates = new ArrayList<KnowledgeAggregate>();
        for (KnowledgeEntity entity : entities) {
            aggregates.add(loadAggregate(entity));
        }
        return KnowledgeMarkdownSerializer.serializeWorkspace(workspaceName, exportTime, aggregates);
    }

    /**
     * 分页查询当前工作空间内已发布经验列表
     */
    public PageResult<KnowledgeAggregate> listPublished(int pageNum, int pageSize, String keyword, String workspaceId) {
        return list(pageNum, pageSize, keyword, MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE,
                MemoryConstants.LIFECYCLE_PUBLISHED, workspaceId);
    }

    /**
     * 查询知识详情，含 Facts 与 Artifacts，并校验工作空间归属
     */
    public KnowledgeAggregate getDetail(Long knowledgeId, String workspaceId) {
        KnowledgeEntity entity = requireKnowledgeInWorkspace(knowledgeId, workspaceId);
        return loadAggregate(entity);
    }

    /**
     * 查询单条经验的 Feedback 统计摘要
     */
    public KnowledgeFeedbackSummaryVO getFeedbackSummary(Long knowledgeId, String workspaceId) {
        requireKnowledgeInWorkspace(knowledgeId, workspaceId);
        List<MemoryFeedbackEntity> feedbackEntityList = memoryFeedbackMapper.selectList(
                new LambdaQueryWrapper<MemoryFeedbackEntity>()
                        .eq(MemoryFeedbackEntity::getKnowledgeId, knowledgeId));

        int helpfulCount = 0;
        int usedCount = 0;
        int notHelpfulCount = 0;
        int outdatedCount = 0;
        int wrongCount = 0;
        for (MemoryFeedbackEntity feedbackEntity : feedbackEntityList) {
            String feedbackType = feedbackEntity.getFeedbackType();
            if ("helpful".equals(feedbackType)) {
                helpfulCount++;
            } else if ("used".equals(feedbackType)) {
                usedCount++;
            } else if ("not_helpful".equals(feedbackType)) {
                notHelpfulCount++;
            } else if ("outdated".equals(feedbackType)) {
                outdatedCount++;
            } else if ("wrong".equals(feedbackType)) {
                wrongCount++;
            }
        }
        int totalCount = feedbackEntityList.size();
        int positiveCount = helpfulCount + usedCount;

        KnowledgeFeedbackSummaryVO summaryView = new KnowledgeFeedbackSummaryVO();
        summaryView.setKnowledgeId(knowledgeId);
        summaryView.setHelpfulCount(helpfulCount);
        summaryView.setUsedCount(usedCount);
        summaryView.setNotHelpfulCount(notHelpfulCount);
        summaryView.setOutdatedCount(outdatedCount);
        summaryView.setWrongCount(wrongCount);
        summaryView.setTotalCount(totalCount);
        summaryView.setHelpfulRate(totalCount == 0 ? 0D : roundRate((double) positiveCount / totalCount));
        return summaryView;
    }

    /**
     * 校验知识类型是否合法
     */
    public void validateKnowledgeType(String knowledgeType) {
        if (StringUtils.isBlank(knowledgeType)) {
            return;
        }
        if (!MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(knowledgeType)
                && !MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(knowledgeType)
                && !MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(knowledgeType)
                && !MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(knowledgeType)) {
            throw new BusinessException(400, "knowledgeType 仅支持 experience、rule、workflow、decision");
        }
    }

    /**
     * Agent 直达写入知识时的轻量校验：须指定类型且至少一条有效 Fact
     */
    public void validateDirectStoreRequest(KnowledgeSaveRequest saveRequest) {
        if (saveRequest == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String knowledgeType = StringUtils.isNotBlank(saveRequest.getKnowledgeType())
                ? saveRequest.getKnowledgeType()
                : MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE;
        validateKnowledgeType(knowledgeType);
        if (StringUtils.isBlank(saveRequest.getTitle())) {
            throw new BusinessException(400, "title 不能为空");
        }
        if (!hasValidFact(saveRequest.getFacts())) {
            throw new BusinessException(400, "至少提供一条非空 Fact Block");
        }
    }

    /**
     * 人工创建知识，写入当前工作空间
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeSaveRequest saveRequest, Long creatorId, String workspaceId) {
        return create(saveRequest, creatorId, workspaceId, null);
    }

    /**
     * 人工创建知识，可选绑定来源 Capture 草稿
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeSaveRequest saveRequest, Long creatorId, String workspaceId,
                       Long sourceCaptureDraftId) {
        requireWorkspaceId(workspaceId);
        if (creatorId == null) {
            throw new BusinessException(400, "创建者不能为空");
        }
        validateKnowledgeType(saveRequest == null ? null : saveRequest.getKnowledgeType());
        KnowledgeEntity entity = buildEntityFromRequest(saveRequest, creatorId, workspaceId);
        entity.setSourceCaptureDraftId(sourceCaptureDraftId);
        entity.setLifecycleStatus(Boolean.TRUE.equals(saveRequest.getPublish())
                ? MemoryConstants.LIFECYCLE_PUBLISHED
                : MemoryConstants.LIFECYCLE_DRAFT);
        knowledgeMapper.insert(entity);
        saveChildren(entity.getId(), saveRequest);
        if (entity.getLifecycleStatus() == MemoryConstants.LIFECYCLE_PUBLISHED) {
            indexKnowledge(entity.getId());
        }
        return entity.getId();
    }

    /**
     * 批量导入经验:解析 Markdown,逐条创建为草稿(不经 AI,不进 capture_draft)。
     * 解析非法块计入 failures;创建阶段任一异常触发整批事务回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeImportResult importMarkdown(String markdown, Long creatorId, String workspaceId) {
        requireWorkspaceId(workspaceId);
        if (creatorId == null) {
            throw new BusinessException(400, "创建者不能为空");
        }
        List<String> blocks = KnowledgeMarkdownParser.splitBlocks(markdown);
        KnowledgeImportResult result = new KnowledgeImportResult();
        result.setTotal(blocks.size());

        List<KnowledgeSaveRequest> parsed = new ArrayList<KnowledgeSaveRequest>();
        for (int i = 0; i < blocks.size(); i++) {
            String block = blocks.get(i);
            try {
                KnowledgeSaveRequest req = KnowledgeMarkdownParser.parseBlock(block);
                parsed.add(req);
            } catch (BusinessException e) {
                result.getFailures().add(new KnowledgeImportResult.Failure(i + 1, null, e.getMessage()));
            }
        }

        for (KnowledgeSaveRequest req : parsed) {
            req.setPublish(false);
            create(req, creatorId, workspaceId, null);
            result.setImported(result.getImported() + 1);
        }
        result.setFailed(result.getFailures().size());
        return result;
    }

    /**
     * 更新知识内容；编辑角色可改任意经验，发布者可改本人创建的经验
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long knowledgeId, KnowledgeSaveRequest saveRequest, String workspaceId,
                       Long operatorUserId, String memberRole) {
        KnowledgeEntity entity = requireMutableKnowledge(knowledgeId, workspaceId);
        requireModifyPermission(entity, operatorUserId, memberRole);
        applyKnowledgeUpdate(entity, knowledgeId, saveRequest);
    }

    /**
     * 内部更新知识内容（无操作者权限校验，供 Rule 合并等内部流程使用）
     */
    @Transactional(rollbackFor = Exception.class)
    private void updateInternal(Long knowledgeId, KnowledgeSaveRequest saveRequest, String workspaceId) {
        KnowledgeEntity entity = requireMutableKnowledge(knowledgeId, workspaceId);
        applyKnowledgeUpdate(entity, knowledgeId, saveRequest);
    }

    /**
     * 写入知识字段与子表，并在已发布状态下刷新检索索引
     */
    private void applyKnowledgeUpdate(KnowledgeEntity entity, Long knowledgeId, KnowledgeSaveRequest saveRequest) {
        applyRequestToEntity(entity, saveRequest);
        knowledgeMapper.updateById(entity);
        deleteChildren(knowledgeId);
        saveChildren(knowledgeId, saveRequest);
        if (entity.getLifecycleStatus() == MemoryConstants.LIFECYCLE_PUBLISHED) {
            indexKnowledge(knowledgeId);
        }
    }

    /**
     * 下架已发布经验：编辑角色可下架任意经验，发布者可下架本人创建的经验
     */
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDeprecateResultVO deprecate(Long knowledgeId, String workspaceId, Long operatorUserId, String memberRole) {
        KnowledgeEntity entity = requireMutableKnowledge(knowledgeId, workspaceId);
        requireModifyPermission(entity, operatorUserId, memberRole);
        return executeDeprecate(knowledgeId, workspaceId, operatorUserId);
    }

    /**
     * 执行下架逻辑：清理检索索引、保留治理边、写入时间线与级联 Review 提示
     */
    private KnowledgeDeprecateResultVO executeDeprecate(Long knowledgeId, String workspaceId, Long operatorUserId) {
        KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
        if (entity == null || entity.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
            throw new BusinessException(400, "仅已发布经验可下架");
        }
        entity.setLifecycleStatus(MemoryConstants.LIFECYCLE_DEPRECATED);
        knowledgeMapper.updateById(entity);
        retrievalEngine.deleteByKnowledgeId(knowledgeId);
        relationEngine.cleanupRelationsOnDeprecate(knowledgeId, workspaceId);
        knowledgeTimelineService.recordDeprecate(knowledgeId, workspaceId, operatorUserId);

        List<Long> cascadeReviewIdList = cascadeValidationService.createCascadeReviewHints(
                knowledgeId, workspaceId, operatorUserId);

        KnowledgeDeprecateResultVO resultView = new KnowledgeDeprecateResultVO();
        resultView.setKnowledgeId(knowledgeId);
        resultView.setCascadeReviewKnowledgeIds(cascadeReviewIdList);
        resultView.setCascadeReviewCount(cascadeReviewIdList.size());
        return resultView;
    }

    /**
     * 重新启用已失效经验：恢复为已发布并重建检索索引；编辑角色可启用任意经验，发布者可启用本人创建的经验
     */
    @Transactional(rollbackFor = Exception.class)
    public void reactivate(Long knowledgeId, String workspaceId, Long operatorUserId, String memberRole) {
        KnowledgeEntity entity = requireKnowledgeInWorkspace(knowledgeId, workspaceId);
        requireModifyPermission(entity, operatorUserId, memberRole);
        if (entity.getLifecycleStatus() != MemoryConstants.LIFECYCLE_DEPRECATED) {
            throw new BusinessException(400, "仅已失效经验可重新启用");
        }
        entity.setLifecycleStatus(MemoryConstants.LIFECYCLE_PUBLISHED);
        knowledgeMapper.updateById(entity);
        indexKnowledge(knowledgeId);
        relationAsyncService.rebuildInboundAutoRelationsAsync(knowledgeId, workspaceId);
        knowledgeTimelineService.recordReactivate(knowledgeId, workspaceId, operatorUserId);
    }

    /**
     * 删除经验（逻辑删除），并清理检索索引；编辑角色可删任意经验，发布者可删本人创建的经验
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long knowledgeId, String workspaceId, Long operatorUserId, String memberRole) {
        KnowledgeEntity entity = requireKnowledgeInWorkspace(knowledgeId, workspaceId);
        requireDeletePermission(entity, operatorUserId, memberRole);
        deleteChildren(knowledgeId);
        relationEngine.deleteRelationsByKnowledgeId(knowledgeId, workspaceId);
        memoryFeedbackMapper.delete(new LambdaQueryWrapper<MemoryFeedbackEntity>()
                .eq(MemoryFeedbackEntity::getKnowledgeId, knowledgeId));
        knowledgeMapper.deleteById(knowledgeId);
    }

    /**
     * 批量删除知识:逐条独立删除(权限失败/不存在不阻塞其他),每条复用 deleteKnowledge 的独立事务。
     * 注意:本方法不加 @Transactional,否则整批会并入一个事务,违背"逐条独立"的语义。
     */
    public KnowledgeBatchDeleteResult deleteByIds(List<Long> ids, String workspaceId,
                                                  Long operatorUserId, String memberRole) {
        requireWorkspaceId(workspaceId);
        KnowledgeBatchDeleteResult result = new KnowledgeBatchDeleteResult();
        if (ids == null) {
            return result;
        }
        result.setTotal(ids.size());
        for (Long id : ids) {
            try {
                deleteKnowledge(id, workspaceId, operatorUserId, memberRole);
                result.setDeleted(result.getDeleted() + 1);
            } catch (BusinessException e) {
                result.getFailures().add(new KnowledgeBatchDeleteResult.Failure(id, e.getMessage()));
            }
        }
        result.setFailed(result.getFailures().size());
        return result;
    }

    /**
     * 发布知识并建立检索索引；编辑角色可发布任意经验，发布者可发布本人创建的草稿
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long knowledgeId, String workspaceId, Long operatorUserId, String memberRole) {
        KnowledgeEntity entity = requireMutableKnowledge(knowledgeId, workspaceId);
        requireModifyPermission(entity, operatorUserId, memberRole);
        entity.setLifecycleStatus(MemoryConstants.LIFECYCLE_PUBLISHED);
        knowledgeMapper.updateById(entity);
        indexKnowledge(knowledgeId);
        knowledgeTimelineService.recordPublish(knowledgeId, workspaceId, operatorUserId);
    }

    /**
     * 从 Capture 草稿创建并发布经验，绑定到指定工作空间
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromDraft(KnowledgeDraftContent draftContent, Long creatorId, String workspaceId) {
        return createFromDraft(draftContent, creatorId, workspaceId,
                MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE, true, null);
    }

    /**
     * 从 Capture 草稿创建知识，可指定类型与是否直接发布
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromDraft(KnowledgeDraftContent draftContent, Long creatorId, String workspaceId,
                                String knowledgeType, boolean publish) {
        return createFromDraft(draftContent, creatorId, workspaceId, knowledgeType, publish, null);
    }

    /**
     * 从 Capture 草稿创建知识，可写入 source_capture_draft_id 便于闭环追踪
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromDraft(KnowledgeDraftContent draftContent, Long creatorId, String workspaceId,
                                String knowledgeType, boolean publish, Long sourceCaptureDraftId) {
        KnowledgeSaveRequest saveRequest = new KnowledgeSaveRequest();
        saveRequest.setTitle(draftContent.getTitle());
        saveRequest.setProject(draftContent.getProject());
        saveRequest.setModule(draftContent.getModule());
        saveRequest.setRepository(draftContent.getRepository());
        saveRequest.setLanguage(draftContent.getLanguage());
        saveRequest.setFramework(draftContent.getFramework());
        saveRequest.setTags(draftContent.getTags());
        saveRequest.setFacts(draftContent.getFacts());
        saveRequest.setArtifacts(draftContent.getArtifacts());
        saveRequest.setKnowledgeType(knowledgeType);
        saveRequest.setPublish(publish);
        return create(saveRequest, creatorId, workspaceId, sourceCaptureDraftId);
    }

    /**
     * 追加 Fact Block 到已有知识（合并 Rule 时使用，按文本去重）
     */
    @Transactional(rollbackFor = Exception.class)
    public void appendFacts(Long knowledgeId, List<FactBlock> newFacts, String workspaceId) {
        KnowledgeEntity entity = requireMutableKnowledge(knowledgeId, workspaceId);
        if (!MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(entity.getKnowledgeType())) {
            throw new BusinessException(400, "仅 Rule 类型支持合并 Fact");
        }
        KnowledgeAggregate aggregate = loadAggregate(entity);
        List<FactBlock> mergedFacts = new ArrayList<FactBlock>();
        if (aggregate.getFacts() != null) {
            mergedFacts.addAll(aggregate.getFacts());
        }
        if (newFacts != null) {
            for (FactBlock newFact : newFacts) {
                if (newFact == null || StringUtils.isBlank(newFact.getText())) {
                    continue;
                }
                boolean duplicated = false;
                for (FactBlock existing : mergedFacts) {
                    if (existing != null && StringUtils.equals(existing.getText(), newFact.getText())) {
                        duplicated = true;
                        break;
                    }
                }
                if (!duplicated) {
                    mergedFacts.add(newFact);
                }
            }
        }
        KnowledgeSaveRequest saveRequest = buildSaveRequestFromAggregate(aggregate);
        saveRequest.setFacts(mergedFacts);
        updateInternal(knowledgeId, saveRequest, workspaceId);
    }

    /**
     * 递增 Recall 调用次数
     */
    public void increaseRecallCount(Long knowledgeId) {
        LambdaUpdateWrapper<KnowledgeEntity> updateWrapper = new LambdaUpdateWrapper<KnowledgeEntity>();
        updateWrapper.eq(KnowledgeEntity::getId, knowledgeId)
                .setSql("recall_count = recall_count + 1");
        knowledgeMapper.update(null, updateWrapper);
    }

    /**
     * 查询指定工作空间内全部已发布知识 ID，供 Recall 候选集
     * knowledgeTypes 为空时默认仅 experience，保持 MVP 客户端行为不变
     */
    public List<Long> listPublishedKnowledgeIds(String workspaceId, List<String> knowledgeTypes) {
        requireWorkspaceId(workspaceId);
        LambdaQueryWrapper<KnowledgeEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        queryWrapper.eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED)
                .select(KnowledgeEntity::getId);
        if (knowledgeTypes == null || knowledgeTypes.isEmpty()) {
            queryWrapper.eq(KnowledgeEntity::getKnowledgeType, MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
        } else {
            queryWrapper.in(KnowledgeEntity::getKnowledgeType, knowledgeTypes);
        }
        List<KnowledgeEntity> entityList = knowledgeMapper.selectList(queryWrapper);
        List<Long> idList = new ArrayList<Long>();
        for (KnowledgeEntity entity : entityList) {
            idList.add(entity.getId());
        }
        return idList;
    }

    /**
     * 查询指定工作空间内全部已发布知识 ID，供 Search 兜底（兼容旧调用）
     */
    public List<Long> listPublishedKnowledgeIds(String workspaceId) {
        return listPublishedKnowledgeIds(workspaceId, null);
    }

    /**
     * 校验工作空间主键有效
     */
    private void requireWorkspaceId(String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
    }

    /**
     * 校验经验存在且属于当前工作空间
     */
    private KnowledgeEntity requireKnowledgeInWorkspace(Long knowledgeId, String workspaceId) {
        requireWorkspaceId(workspaceId);
        KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
        if (entity == null) {
            throw new BusinessException(404, "经验不存在");
        }
        if (!workspaceId.equals(entity.getWorkspaceId())) {
            throw new BusinessException(403, "无权访问该经验");
        }
        return entity;
    }

    private Double roundRate(double rate) {
        return Math.round(rate * 10000D) / 10000D;
    }

    /**
     * 校验修改权限：Owner/Admin/Editor 可改任意经验；其他角色仅可修改本人发布的经验
     */
    private void requireModifyPermission(KnowledgeEntity entity, Long operatorUserId, String memberRole) {
        if (WorkspaceMemberRole.canModifyKnowledge(memberRole, operatorUserId, entity.getCreatorId())) {
            return;
        }
        throw new BusinessException(403, "无权修改该经验");
    }

    /**
     * 校验删除权限：Owner/Admin 可删任意经验；编辑者及其他角色仅可删除本人创建的经验
     */
    private void requireDeletePermission(KnowledgeEntity entity, Long operatorUserId, String memberRole) {
        if (WorkspaceMemberRole.canDeleteKnowledge(memberRole, operatorUserId, entity.getCreatorId())) {
            return;
        }
        throw new BusinessException(403, "无权删除该经验");
    }

    /**
     * 加载可变更的经验实体，已删除或已失效时拒绝修改类操作
     */
    private KnowledgeEntity requireMutableKnowledge(Long knowledgeId, String workspaceId) {
        KnowledgeEntity entity = requireKnowledgeInWorkspace(knowledgeId, workspaceId);
        if (entity.getLifecycleStatus() == MemoryConstants.LIFECYCLE_DEPRECATED) {
            throw new BusinessException(400, "经验已下架，无法继续操作");
        }
        return entity;
    }

    private void indexKnowledge(Long knowledgeId) {
        KnowledgeAggregate aggregate = loadAggregate(knowledgeMapper.selectById(knowledgeId));
        String indexText = buildIndexText(aggregate);
        KnowledgeEntity entity = knowledgeMapper.selectById(knowledgeId);
        retrievalEngine.indexKnowledge(entity, indexText);
        relationAsyncService.buildRelationsAsync(knowledgeId, entity.getWorkspaceId());
    }

    private String buildIndexText(KnowledgeAggregate aggregate) {
        StringBuilder builder = new StringBuilder();
        builder.append(aggregate.getTitle()).append(" ");
        builder.append(StringUtils.defaultString(aggregate.getProject())).append(" ");
        builder.append(StringUtils.defaultString(aggregate.getModule())).append(" ");
        builder.append(StringUtils.defaultString(aggregate.getRepository())).append(" ");
        if (aggregate.getTags() != null) {
            builder.append(StringUtils.join(aggregate.getTags(), " ")).append(" ");
        }
        if (aggregate.getFacts() != null) {
            for (FactBlock factBlock : aggregate.getFacts()) {
                builder.append(factBlock.getText()).append(" ");
            }
        }
        return builder.toString().trim();
    }

    private KnowledgeEntity buildEntityFromRequest(KnowledgeSaveRequest saveRequest, Long creatorId, String workspaceId) {
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setWorkspaceId(workspaceId);
        String knowledgeType = StringUtils.isNotBlank(saveRequest.getKnowledgeType())
                ? saveRequest.getKnowledgeType()
                : MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE;
        entity.setKnowledgeType(knowledgeType);
        entity.setCreatorId(creatorId);
        entity.setRecallCount(0);
        applyRequestToEntity(entity, saveRequest);
        return entity;
    }

    private KnowledgeSaveRequest buildSaveRequestFromAggregate(KnowledgeAggregate aggregate) {
        KnowledgeSaveRequest saveRequest = new KnowledgeSaveRequest();
        saveRequest.setTitle(aggregate.getTitle());
        saveRequest.setProject(aggregate.getProject());
        saveRequest.setModule(aggregate.getModule());
        saveRequest.setRepository(aggregate.getRepository());
        saveRequest.setLanguage(aggregate.getLanguage());
        saveRequest.setFramework(aggregate.getFramework());
        saveRequest.setTags(aggregate.getTags());
        saveRequest.setFacts(aggregate.getFacts());
        saveRequest.setArtifacts(aggregate.getArtifacts());
        saveRequest.setKnowledgeType(aggregate.getKnowledgeType());
        return saveRequest;
    }

    private void applyRequestToEntity(KnowledgeEntity entity, KnowledgeSaveRequest saveRequest) {
        contextNormalizer.normalizeSaveRequest(saveRequest);
        entity.setTitle(saveRequest.getTitle());
        entity.setProject(saveRequest.getProject());
        entity.setModule(saveRequest.getModule());
        entity.setRepository(saveRequest.getRepository());
        entity.setLanguage(saveRequest.getLanguage());
        entity.setFramework(saveRequest.getFramework());
    }

    private void saveChildren(Long knowledgeId, KnowledgeSaveRequest saveRequest) {
        if (saveRequest.getTags() != null) {
            for (String tagName : MemoryJsonUtil.sanitizeTags(saveRequest.getTags())) {
                knowledgeTagMapper.insertTag(knowledgeId, tagName);
            }
        }
        if (saveRequest.getFacts() != null) {
            int sortOrder = 0;
            for (FactBlock factBlock : saveRequest.getFacts()) {
                if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                    continue;
                }
                if (StringUtils.isBlank(factBlock.getType())) {
                    factBlock.setType("observation");
                }
                knowledgeFactMapper.insert(MemoryJsonUtil.toFactEntity(knowledgeId, factBlock, sortOrder++));
            }
        }
        if (saveRequest.getArtifacts() != null) {
            for (ArtifactDto artifactDto : saveRequest.getArtifacts()) {
                if (artifactDto == null || StringUtils.isBlank(artifactDto.getArtifactType())) {
                    continue;
                }
                if (StringUtils.isBlank(artifactDto.getArtifactRole())) {
                    artifactDto.setArtifactRole("origin");
                }
                knowledgeArtifactMapper.insert(MemoryJsonUtil.toArtifactEntity(knowledgeId, artifactDto));
            }
        }
    }

    private void deleteChildren(Long knowledgeId) {
        LambdaQueryWrapper<KnowledgeFactEntity> factWrapper = new LambdaQueryWrapper<KnowledgeFactEntity>();
        factWrapper.eq(KnowledgeFactEntity::getKnowledgeId, knowledgeId);
        knowledgeFactMapper.delete(factWrapper);

        LambdaQueryWrapper<KnowledgeArtifactEntity> artifactWrapper = new LambdaQueryWrapper<KnowledgeArtifactEntity>();
        artifactWrapper.eq(KnowledgeArtifactEntity::getKnowledgeId, knowledgeId);
        knowledgeArtifactMapper.delete(artifactWrapper);

        knowledgeTagMapper.deleteByKnowledgeId(knowledgeId);
        retrievalEngine.deleteByKnowledgeId(knowledgeId);
    }

    private KnowledgeAggregate toAggregateSummary(KnowledgeEntity entity) {
        KnowledgeAggregate aggregate = new KnowledgeAggregate();
        aggregate.setId(entity.getId());
        aggregate.setWorkspaceId(entity.getWorkspaceId());
        aggregate.setKnowledgeType(entity.getKnowledgeType());
        aggregate.setTitle(entity.getTitle());
        aggregate.setProject(entity.getProject());
        aggregate.setModule(entity.getModule());
        aggregate.setRepository(entity.getRepository());
        aggregate.setLifecycleStatus(entity.getLifecycleStatus());
        aggregate.setRecallCount(entity.getRecallCount());
        aggregate.setCreatorId(entity.getCreatorId());
        aggregate.setSourceCaptureDraftId(entity.getSourceCaptureDraftId());
        aggregate.setCreateTime(entity.getCreateTime());
        aggregate.setUpdateTime(entity.getUpdateTime());
        aggregate.setTags(knowledgeTagMapper.selectTagNames(entity.getId()));
        return aggregate;
    }

    private KnowledgeAggregate loadAggregate(KnowledgeEntity entity) {
        KnowledgeAggregate aggregate = toAggregateSummary(entity);
        aggregate.setLanguage(entity.getLanguage());
        aggregate.setFramework(entity.getFramework());

        LambdaQueryWrapper<KnowledgeFactEntity> factWrapper = new LambdaQueryWrapper<KnowledgeFactEntity>();
        factWrapper.eq(KnowledgeFactEntity::getKnowledgeId, entity.getId())
                .orderByAsc(KnowledgeFactEntity::getSortOrder);
        List<KnowledgeFactEntity> factEntities = knowledgeFactMapper.selectList(factWrapper);
        List<FactBlock> factBlocks = new ArrayList<FactBlock>();
        for (KnowledgeFactEntity factEntity : factEntities) {
            factBlocks.add(MemoryJsonUtil.toFactBlock(factEntity));
        }
        aggregate.setFacts(factBlocks);

        LambdaQueryWrapper<KnowledgeArtifactEntity> artifactWrapper = new LambdaQueryWrapper<KnowledgeArtifactEntity>();
        artifactWrapper.eq(KnowledgeArtifactEntity::getKnowledgeId, entity.getId());
        List<KnowledgeArtifactEntity> artifactEntities = knowledgeArtifactMapper.selectList(artifactWrapper);
        List<ArtifactDto> artifactDtos = new ArrayList<ArtifactDto>();
        for (KnowledgeArtifactEntity artifactEntity : artifactEntities) {
            artifactDtos.add(MemoryJsonUtil.toArtifactDto(artifactEntity));
        }
        aggregate.setArtifacts(artifactDtos);
        enrichCreatorProfile(aggregate);
        return aggregate;
    }

    /**
     * 为单条知识补全提交人昵称与头像
     */
    private void enrichCreatorProfile(KnowledgeAggregate aggregate) {
        if (aggregate == null || aggregate.getCreatorId() == null) {
            return;
        }
        UserProfileBrief profileBrief = userProfileService.resolveOne(aggregate.getCreatorId());
        applyCreatorProfile(aggregate, profileBrief);
    }

    /**
     * 为知识列表批量补全提交人信息
     */
    private void enrichCreatorProfiles(List<KnowledgeAggregate> aggregateList) {
        if (aggregateList == null || aggregateList.isEmpty()) {
            return;
        }
        Set<Long> creatorIdSet = new HashSet<Long>();
        for (KnowledgeAggregate aggregate : aggregateList) {
            if (aggregate != null && aggregate.getCreatorId() != null) {
                creatorIdSet.add(aggregate.getCreatorId());
            }
        }
        if (creatorIdSet.isEmpty()) {
            return;
        }
        Map<Long, UserProfileBrief> profileMap = userProfileService.batchResolve(creatorIdSet);
        for (KnowledgeAggregate aggregate : aggregateList) {
            if (aggregate == null || aggregate.getCreatorId() == null) {
                continue;
            }
            applyCreatorProfile(aggregate, profileMap.get(aggregate.getCreatorId()));
        }
    }

    /**
     * 将用户展示摘要写入知识聚合对象
     */
    private void applyCreatorProfile(KnowledgeAggregate aggregate, UserProfileBrief profileBrief) {
        if (aggregate == null || profileBrief == null) {
            return;
        }
        aggregate.setCreatorNickname(profileBrief.getNickname());
        aggregate.setCreatorAvatar(profileBrief.getAvatar());
    }

    /**
     * 判断 Fact 列表中是否至少有一条非空文本
     */
    private boolean hasValidFact(List<FactBlock> factBlockList) {
        if (factBlockList == null || factBlockList.isEmpty()) {
            return false;
        }
        for (FactBlock factBlock : factBlockList) {
            if (factBlock != null && StringUtils.isNotBlank(factBlock.getText())) {
                return true;
            }
        }
        return false;
    }
}
