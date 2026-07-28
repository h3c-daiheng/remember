package com.zhiyi.memory.governance;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.PageResult;
import com.zhiyi.domain.vo.KnowledgeSupersedeRequest;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.GovernanceIssueMapper;
import com.zhiyi.memory.dao.GovernanceScanBatchMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.config.GovernanceProperties;
import com.zhiyi.memory.domain.GovernanceConfigUpdateRequest;
import com.zhiyi.memory.domain.GovernanceConfigView;
import com.zhiyi.memory.domain.GovernanceFunnelView;
import com.zhiyi.memory.domain.GovernanceIssueView;
import com.zhiyi.memory.domain.GovernanceMergeConfirmRequest;
import com.zhiyi.memory.domain.GovernanceMergePreviewRequest;
import com.zhiyi.memory.domain.GovernanceMergePreviewView;
import com.zhiyi.memory.domain.GovernanceResolveRequest;
import com.zhiyi.memory.domain.GovernanceScanBatchView;
import com.zhiyi.memory.domain.GovernanceScanRequest;
import com.zhiyi.memory.domain.GovernanceStatsView;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.entity.GovernanceIssueEntity;
import com.zhiyi.memory.entity.GovernanceScanBatchEntity;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.graph.GraphGovernanceService;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 记忆治理服务：重复扫描、碎片聚类、合并优化与工单处置
 */
@Service
public class GovernanceService {

    private final GovernanceIssueMapper governanceIssueMapper;
    private final GovernanceScanBatchMapper governanceScanBatchMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final DuplicateScanner duplicateScanner;
    private final ClusterEngine clusterEngine;
    private final MergeEngine mergeEngine;
    private final KnowledgeService knowledgeService;
    private final GraphGovernanceService graphGovernanceService;
    private final KnowledgeTimelineService knowledgeTimelineService;
    private final ValidateEngine validateEngine;
    private final GovernanceAutoResolveService governanceAutoResolveService;
    private final GovernanceProperties governanceProperties;

    public GovernanceService(GovernanceIssueMapper governanceIssueMapper,
                             GovernanceScanBatchMapper governanceScanBatchMapper,
                             KnowledgeMapper knowledgeMapper,
                             DuplicateScanner duplicateScanner,
                             ClusterEngine clusterEngine,
                             MergeEngine mergeEngine,
                             KnowledgeService knowledgeService,
                             GraphGovernanceService graphGovernanceService,
                             KnowledgeTimelineService knowledgeTimelineService,
                             ValidateEngine validateEngine,
                             GovernanceAutoResolveService governanceAutoResolveService,
                             GovernanceProperties governanceProperties) {
        this.governanceIssueMapper = governanceIssueMapper;
        this.governanceScanBatchMapper = governanceScanBatchMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.duplicateScanner = duplicateScanner;
        this.clusterEngine = clusterEngine;
        this.mergeEngine = mergeEngine;
        this.knowledgeService = knowledgeService;
        this.graphGovernanceService = graphGovernanceService;
        this.knowledgeTimelineService = knowledgeTimelineService;
        this.validateEngine = validateEngine;
        this.governanceAutoResolveService = governanceAutoResolveService;
        this.governanceProperties = governanceProperties;
    }

    /**
     * 触发治理扫描：按 scanMode 分发重复扫描或碎片聚类
     */
    @Transactional(rollbackFor = Exception.class)
    public GovernanceScanBatchView runScan(GovernanceScanRequest scanRequest,
                                           String workspaceId,
                                           Long operatorId) {
        String scanMode = resolveScanMode(scanRequest);
        if (GovernanceConstants.SCAN_TYPE_VALIDATE.equals(scanMode)
                || GovernanceConstants.SCAN_MODE_VALIDATE.equals(scanMode)) {
            return runValidateScan(scanRequest, workspaceId, operatorId);
        }
        if (GovernanceConstants.SCAN_TYPE_FRAGMENT.equals(scanMode)) {
            return runFragmentScan(scanRequest, workspaceId, operatorId);
        }
        return runDuplicateScan(scanRequest, workspaceId, operatorId);
    }

    /**
     * 定时任务入口：增量重复扫描 + 质量校验
     */
    public void runScheduledWorkspaceGovernance(String workspaceId) {
        if (governanceProperties.isIncrementalScanEnabled()) {
            GovernanceScanRequest incrementalRequest = new GovernanceScanRequest();
            incrementalRequest.setScanType(GovernanceConstants.SCAN_TYPE_INCREMENTAL);
            incrementalRequest.setScanMode(GovernanceConstants.SCAN_MODE_DUPLICATE);
            runDuplicateScan(incrementalRequest, workspaceId, null);
        }
        if (governanceProperties.isValidateScanEnabled()) {
            GovernanceScanRequest validateRequest = new GovernanceScanRequest();
            validateRequest.setScanType(GovernanceConstants.SCAN_TYPE_VALIDATE);
            validateRequest.setScanMode(GovernanceConstants.SCAN_MODE_VALIDATE);
            runValidateScan(validateRequest, workspaceId, null);
        }
    }

    /**
     * 触发重复扫描并生成治理工单
     */
    @Transactional(rollbackFor = Exception.class)
    public GovernanceScanBatchView runDuplicateScan(GovernanceScanRequest scanRequest,
                                                      String workspaceId,
                                                      Long operatorId) {
        requireWorkspaceId(workspaceId);
        String scanType = resolveScanType(scanRequest);
        String knowledgeTypeFilter = scanRequest == null ? null : scanRequest.getKnowledgeType();
        String moduleFilter = scanRequest == null ? null : scanRequest.getModuleFilter();
        double threshold = resolveThreshold(scanRequest);

        GovernanceScanBatchEntity batchEntity = new GovernanceScanBatchEntity();
        batchEntity.setWorkspaceId(workspaceId);
        batchEntity.setScanType(scanType);
        batchEntity.setKnowledgeType(knowledgeTypeFilter);
        batchEntity.setModuleFilter(moduleFilter);
        batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_RUNNING);
        batchEntity.setScannedCount(0);
        batchEntity.setIssueCount(0);
        batchEntity.setSimilarityThreshold(threshold);
        batchEntity.setOperatorId(operatorId);
        governanceScanBatchMapper.insert(batchEntity);

        try {
            List<DuplicateGroupScanResult> groupList;
            if (GovernanceConstants.SCAN_TYPE_INCREMENTAL.equals(scanType)) {
                Date changedSince = findLastScanFinishTime(workspaceId, GovernanceConstants.SCAN_TYPE_INCREMENTAL);
                if (changedSince == null) {
                    changedSince = findLastScanFinishTime(workspaceId, GovernanceConstants.SCAN_TYPE_FULL);
                }
                groupList = duplicateScanner.scanDuplicateGroupsIncremental(
                        workspaceId, knowledgeTypeFilter, moduleFilter, threshold, changedSince);
            } else {
                groupList = duplicateScanner.scanDuplicateGroups(
                        workspaceId, knowledgeTypeFilter, moduleFilter, threshold);
            }
            int createdIssueCount = 0;
            Set<String> existingSignatureSet = loadOpenIssueSignatures(workspaceId);
            for (DuplicateGroupScanResult groupResult : groupList) {
                String signature = buildIssueSignature(groupResult.getPrimaryKnowledgeId(),
                        groupResult.getDuplicateKnowledgeIds());
                if (existingSignatureSet.contains(signature)) {
                    continue;
                }
                GovernanceIssueEntity issueEntity = buildIssueEntity(groupResult, workspaceId, batchEntity.getId());
                governanceIssueMapper.insert(issueEntity);
                existingSignatureSet.add(signature);
                createdIssueCount++;
            }

            batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_DONE);
            batchEntity.setScannedCount(countPublishedKnowledge(workspaceId, knowledgeTypeFilter, moduleFilter));
            batchEntity.setIssueCount(createdIssueCount);
            int autoResolvedCount = governanceAutoResolveService.autoResolveDuplicateIssues(
                    workspaceId, batchEntity.getId());
            batchEntity.setAutoResolvedCount(autoResolvedCount);
            batchEntity.setFinishTime(new Date());
            governanceScanBatchMapper.updateById(batchEntity);
            return toBatchView(batchEntity);
        } catch (Exception exception) {
            batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_FAILED);
            batchEntity.setErrorMessage(StringUtils.left(exception.getMessage(), 512));
            batchEntity.setFinishTime(new Date());
            governanceScanBatchMapper.updateById(batchEntity);
            throw new BusinessException(500, "治理扫描失败：" + exception.getMessage());
        }
    }

    /**
     * 触发碎片聚类扫描并生成 merge_optimize 工单
     */
    @Transactional(rollbackFor = Exception.class)
    public GovernanceScanBatchView runFragmentScan(GovernanceScanRequest scanRequest,
                                                   String workspaceId,
                                                   Long operatorId) {
        requireWorkspaceId(workspaceId);
        String knowledgeTypeFilter = scanRequest == null ? null : scanRequest.getKnowledgeType();
        String moduleFilter = scanRequest == null ? null : scanRequest.getModuleFilter();
        double threshold = resolveFragmentThreshold(scanRequest);

        GovernanceScanBatchEntity batchEntity = new GovernanceScanBatchEntity();
        batchEntity.setWorkspaceId(workspaceId);
        batchEntity.setScanType(GovernanceConstants.SCAN_TYPE_FRAGMENT);
        batchEntity.setKnowledgeType(knowledgeTypeFilter);
        batchEntity.setModuleFilter(moduleFilter);
        batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_RUNNING);
        batchEntity.setScannedCount(0);
        batchEntity.setIssueCount(0);
        batchEntity.setSimilarityThreshold(threshold);
        batchEntity.setOperatorId(operatorId);
        governanceScanBatchMapper.insert(batchEntity);

        try {
            List<FragmentClusterScanResult> clusterList = clusterEngine.scanFragmentClusters(
                    workspaceId, knowledgeTypeFilter, moduleFilter, threshold);
            int createdIssueCount = 0;
            Set<String> existingSignatureSet = loadOpenIssueSignatures(workspaceId,
                    GovernanceConstants.ISSUE_TYPE_FRAGMENT_CLUSTER);
            for (FragmentClusterScanResult clusterResult : clusterList) {
                String signature = buildIssueSignature(clusterResult.getPrimaryKnowledgeId(),
                        clusterResult.getMemberKnowledgeIds());
                if (existingSignatureSet.contains(signature)) {
                    continue;
                }
                GovernanceIssueEntity issueEntity = buildFragmentIssueEntity(clusterResult, workspaceId, batchEntity.getId());
                governanceIssueMapper.insert(issueEntity);
                existingSignatureSet.add(signature);
                createdIssueCount++;
            }

            batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_DONE);
            batchEntity.setScannedCount(countPublishedKnowledge(workspaceId, knowledgeTypeFilter, moduleFilter));
            batchEntity.setIssueCount(createdIssueCount);
            batchEntity.setFinishTime(new Date());
            governanceScanBatchMapper.updateById(batchEntity);
            return toBatchView(batchEntity);
        } catch (Exception exception) {
            batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_FAILED);
            batchEntity.setErrorMessage(StringUtils.left(exception.getMessage(), 512));
            batchEntity.setFinishTime(new Date());
            governanceScanBatchMapper.updateById(batchEntity);
            throw new BusinessException(500, "碎片聚类扫描失败：" + exception.getMessage());
        }
    }

    /**
     * 质量校验扫描：不完整记忆与 Rule 约束冲突
     */
    @Transactional(rollbackFor = Exception.class)
    public GovernanceScanBatchView runValidateScan(GovernanceScanRequest scanRequest,
                                                   String workspaceId,
                                                   Long operatorId) {
        requireWorkspaceId(workspaceId);
        String knowledgeTypeFilter = scanRequest == null ? null : scanRequest.getKnowledgeType();
        String moduleFilter = scanRequest == null ? null : scanRequest.getModuleFilter();

        GovernanceScanBatchEntity batchEntity = new GovernanceScanBatchEntity();
        batchEntity.setWorkspaceId(workspaceId);
        batchEntity.setScanType(GovernanceConstants.SCAN_TYPE_VALIDATE);
        batchEntity.setKnowledgeType(knowledgeTypeFilter);
        batchEntity.setModuleFilter(moduleFilter);
        batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_RUNNING);
        batchEntity.setScannedCount(0);
        batchEntity.setIssueCount(0);
        batchEntity.setSimilarityThreshold(GovernanceConstants.DEFAULT_SIMILARITY_THRESHOLD);
        batchEntity.setAutoResolvedCount(0);
        batchEntity.setOperatorId(operatorId);
        governanceScanBatchMapper.insert(batchEntity);

        try {
            List<ValidationScanResult> validationResultList = validateEngine.scanValidationIssues(
                    workspaceId, knowledgeTypeFilter, moduleFilter);
            int createdIssueCount = 0;
            Set<String> existingSignatureSet = loadOpenValidationSignatures(workspaceId);
            for (ValidationScanResult validationResult : validationResultList) {
                String signature = buildValidationSignature(validationResult);
                if (existingSignatureSet.contains(signature)) {
                    continue;
                }
                GovernanceIssueEntity issueEntity = buildValidationIssueEntity(
                        validationResult, workspaceId, batchEntity.getId());
                governanceIssueMapper.insert(issueEntity);
                existingSignatureSet.add(signature);
                createdIssueCount++;
            }

            batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_DONE);
            batchEntity.setScannedCount(countPublishedKnowledge(workspaceId, knowledgeTypeFilter, moduleFilter));
            batchEntity.setIssueCount(createdIssueCount);
            batchEntity.setFinishTime(new Date());
            governanceScanBatchMapper.updateById(batchEntity);
            return toBatchView(batchEntity);
        } catch (Exception exception) {
            batchEntity.setStatus(GovernanceConstants.SCAN_STATUS_FAILED);
            batchEntity.setErrorMessage(StringUtils.left(exception.getMessage(), 512));
            batchEntity.setFinishTime(new Date());
            governanceScanBatchMapper.updateById(batchEntity);
            throw new BusinessException(500, "质量校验扫描失败：" + exception.getMessage());
        }
    }

    /**
     * 生成合并预览：来自工单或手动多选记忆
     */
    public GovernanceMergePreviewView previewMerge(GovernanceMergePreviewRequest previewRequest,
                                                   String workspaceId) {
        requireWorkspaceId(workspaceId);
        List<Long> sourceIds = resolveSourceKnowledgeIds(previewRequest, workspaceId);
        boolean useLlm = previewRequest == null || previewRequest.getUseLlm() == null
                || Boolean.TRUE.equals(previewRequest.getUseLlm());
        return mergeEngine.buildPreview(sourceIds, workspaceId, useLlm);
    }

    /**
     * 确认合并优化：发布新版并关闭关联工单
     */
    @Transactional(rollbackFor = Exception.class)
    public Long confirmMerge(GovernanceMergeConfirmRequest confirmRequest,
                             String workspaceId,
                             Long operatorId,
                             String memberRole) {
        if (confirmRequest != null && confirmRequest.getIssueId() != null) {
            validateMergeConfirmMatchesIssue(confirmRequest, workspaceId);
        }
        Long newKnowledgeId = mergeEngine.confirmMerge(confirmRequest, workspaceId, operatorId, memberRole);
        if (confirmRequest != null && confirmRequest.getIssueId() != null) {
            closeIssueAfterMerge(confirmRequest.getIssueId(), workspaceId, operatorId,
                    StringUtils.defaultString(confirmRequest.getComment(), "合并优化完成"));
        }
        return newKnowledgeId;
    }

    /**
     * 分页查询治理工单
     */
    public PageResult<GovernanceIssueView> listIssues(int pageNum,
                                                      int pageSize,
                                                      Integer status,
                                                      String issueType,
                                                      String workspaceId) {
        requireWorkspaceId(workspaceId);
        PageHelper.startPage(pageNum, pageSize);
        LambdaQueryWrapper<GovernanceIssueEntity> queryWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        queryWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId);
        if (status != null) {
            queryWrapper.eq(GovernanceIssueEntity::getStatus, status);
        }
        if (StringUtils.isNotBlank(issueType)) {
            queryWrapper.eq(GovernanceIssueEntity::getIssueType, issueType);
        }
        queryWrapper.orderByDesc(GovernanceIssueEntity::getSimilarityScore)
                .orderByDesc(GovernanceIssueEntity::getCreateTime);
        List<GovernanceIssueEntity> entityList = governanceIssueMapper.selectList(queryWrapper);
        PageInfo<GovernanceIssueEntity> pageInfo = new PageInfo<GovernanceIssueEntity>(entityList);

        List<GovernanceIssueView> viewList = new ArrayList<GovernanceIssueView>();
        for (GovernanceIssueEntity entity : entityList) {
            viewList.add(toIssueSummaryView(entity, workspaceId));
        }
        return PageResult.of(pageInfo.getTotal(), viewList);
    }

    /**
     * 查询治理工单详情，含主版本与重复记忆完整内容
     */
    public GovernanceIssueView getIssueDetail(Long issueId, String workspaceId) {
        GovernanceIssueEntity entity = requireIssueInWorkspace(issueId, workspaceId);
        GovernanceIssueView issueView = toIssueDetailView(entity, workspaceId);
        return issueView;
    }

    /**
     * 处置治理工单：保留主版本 / 合并 Fact / 忽略误报
     */
    @Transactional(rollbackFor = Exception.class)
    public GovernanceIssueView resolveIssue(Long issueId,
                                            GovernanceResolveRequest resolveRequest,
                                            String workspaceId,
                                            Long operatorId,
                                            String memberRole) {
        GovernanceIssueEntity issueEntity = requireIssueInWorkspace(issueId, workspaceId);
        if (issueEntity.getStatus() != GovernanceConstants.ISSUE_STATUS_OPEN) {
            throw new BusinessException(400, "工单已处理");
        }
        if (resolveRequest == null || StringUtils.isBlank(resolveRequest.getAction())) {
            throw new BusinessException(400, "缺少处置动作 action");
        }

        String action = resolveRequest.getAction().trim();
        validateResolveActionForIssueType(action, issueEntity.getIssueType());

        if (GovernanceConstants.ACTION_DISMISS.equals(action)) {
            issueEntity.setStatus(GovernanceConstants.ISSUE_STATUS_DISMISSED);
            issueEntity.setResolvedAction(action);
            issueEntity.setResolveComment(resolveRequest.getComment());
            issueEntity.setOperatorId(operatorId);
            issueEntity.setResolveTime(new Date());
            governanceIssueMapper.updateById(issueEntity);
            knowledgeTimelineService.recordGovernanceDismiss(
                    issueEntity.getPrimaryKnowledgeId(), workspaceId, operatorId, resolveRequest.getComment());
            return getIssueDetail(issueId, workspaceId);
        }

        Long primaryKnowledgeId = resolveRequest.getPrimaryKnowledgeId() != null
                ? resolveRequest.getPrimaryKnowledgeId()
                : issueEntity.getPrimaryKnowledgeId();
        List<Long> duplicateIdList = buildDuplicateIdListExcludingPrimary(issueEntity, primaryKnowledgeId);

        if (GovernanceConstants.ACTION_MERGE_FACTS.equals(action)) {
            executeMergeFacts(primaryKnowledgeId, duplicateIdList, workspaceId, operatorId, memberRole,
                    resolveRequest.getComment());
            issueEntity.setResolvedAction(action);
        } else if (GovernanceConstants.ACTION_KEEP_PRIMARY_DEPRECATE_OTHERS.equals(action)) {
            executeKeepPrimaryDeprecateOthers(primaryKnowledgeId, duplicateIdList, workspaceId, operatorId,
                    memberRole, resolveRequest.getComment());
            issueEntity.setResolvedAction(action);
        } else if (GovernanceConstants.ACTION_MERGE_OPTIMIZE.equals(action)) {
            executeMergeOptimize(issueEntity, resolveRequest, workspaceId, operatorId, memberRole);
            issueEntity.setResolvedAction(action);
        } else if (GovernanceConstants.ACTION_DEPRECATE.equals(action)) {
            knowledgeService.deprecate(primaryKnowledgeId, workspaceId, operatorId, memberRole);
            knowledgeTimelineService.recordDeprecate(primaryKnowledgeId, workspaceId, operatorId);
            issueEntity.setResolvedAction(action);
        } else {
            throw new BusinessException(400, "不支持的处置动作：" + action);
        }

        issueEntity.setStatus(GovernanceConstants.ISSUE_STATUS_RESOLVED);
        issueEntity.setPrimaryKnowledgeId(primaryKnowledgeId);
        issueEntity.setResolveComment(resolveRequest.getComment());
        issueEntity.setOperatorId(operatorId);
        issueEntity.setResolveTime(new Date());
        governanceIssueMapper.updateById(issueEntity);
        return getIssueDetail(issueId, workspaceId);
    }

    /**
     * 治理统计摘要（含质量工单与漏斗）
     */
    public GovernanceStatsView getStats(String workspaceId) {
        requireWorkspaceId(workspaceId);
        GovernanceStatsView statsView = new GovernanceStatsView();
        statsView.setOpenIssueCount(countOpenIssuesByType(
                workspaceId, GovernanceConstants.ISSUE_TYPE_DUPLICATE));
        statsView.setResolvedIssueCount(countIssuesByStatus(workspaceId, GovernanceConstants.ISSUE_STATUS_RESOLVED));
        statsView.setDismissedIssueCount(countIssuesByStatus(workspaceId, GovernanceConstants.ISSUE_STATUS_DISMISSED));
        statsView.setOpenFragmentIssueCount(countOpenIssuesByType(
                workspaceId, GovernanceConstants.ISSUE_TYPE_FRAGMENT_CLUSTER));
        statsView.setOpenIncompleteIssueCount(countOpenIssuesByType(
                workspaceId, GovernanceConstants.ISSUE_TYPE_INCOMPLETE));
        statsView.setOpenOutdatedIssueCount(countOpenIssuesByType(
                workspaceId, GovernanceConstants.ISSUE_TYPE_OUTDATED));
        statsView.setOpenConflictIssueCount(countOpenIssuesByType(
                workspaceId, GovernanceConstants.ISSUE_TYPE_CONFLICT));
        statsView.setPublishedKnowledgeCount(countPublishedKnowledge(workspaceId, null, null));
        statsView.setAutoResolvedIssueCount(countAutoResolvedIssues(workspaceId));

        int publishedCount = statsView.getPublishedKnowledgeCount();
        if (publishedCount > 0) {
            statsView.setDuplicateIssueRate((double) statsView.getOpenIssueCount() / (double) publishedCount);
        } else {
            statsView.setDuplicateIssueRate(0D);
        }

        statsView.setLastScanTime(findLastScanFinishTime(workspaceId, GovernanceConstants.SCAN_TYPE_FULL));
        statsView.setLastFragmentScanTime(findLastScanFinishTime(workspaceId, GovernanceConstants.SCAN_TYPE_FRAGMENT));
        statsView.setLastIncrementalScanTime(findLastScanFinishTime(
                workspaceId, GovernanceConstants.SCAN_TYPE_INCREMENTAL));
        statsView.setLastValidateScanTime(findLastScanFinishTime(workspaceId, GovernanceConstants.SCAN_TYPE_VALIDATE));
        statsView.setFunnel(buildFunnelStats(workspaceId));
        return statsView;
    }

    /**
     * 查询工作空间治理配置（全局默认 + 工作空间覆盖）
     */
    public GovernanceConfigView getConfig(String workspaceId) {
        requireWorkspaceId(workspaceId);
        GovernanceWorkspaceConfig workspaceConfig = governanceAutoResolveService.loadWorkspaceConfig(workspaceId);
        GovernanceConfigView configView = new GovernanceConfigView();
        configView.setAutoResolveEnabled(workspaceConfig.isAutoResolveEnabled());
        configView.setAutoResolveSimilarityThreshold(workspaceConfig.getAutoResolveSimilarityThreshold());
        configView.setGlobalAutoResolveEnabled(governanceProperties.isAutoResolveEnabled());
        configView.setGlobalAutoResolveSimilarityThreshold(governanceProperties.getAutoResolveSimilarityThreshold());
        configView.setSchedulerEnabled(governanceProperties.isSchedulerEnabled());
        configView.setFeedbackTriggerEnabled(governanceProperties.isFeedbackTriggerEnabled());
        configView.setOutdatedFeedbackThreshold(governanceProperties.getOutdatedFeedbackThreshold());
        return configView;
    }

    /**
     * 更新工作空间治理配置（仅 autoResolve 相关项）
     */
    @Transactional(rollbackFor = Exception.class)
    public GovernanceConfigView updateConfig(GovernanceConfigUpdateRequest updateRequest, String workspaceId) {
        requireWorkspaceId(workspaceId);

        GovernanceWorkspaceConfig currentConfig = governanceAutoResolveService.loadWorkspaceConfig(workspaceId);
        boolean autoResolveEnabled = updateRequest != null && updateRequest.getAutoResolveEnabled() != null
                ? updateRequest.getAutoResolveEnabled().booleanValue()
                : currentConfig.isAutoResolveEnabled();
        double autoResolveThreshold = updateRequest != null && updateRequest.getAutoResolveSimilarityThreshold() != null
                ? updateRequest.getAutoResolveSimilarityThreshold().doubleValue()
                : currentConfig.getAutoResolveSimilarityThreshold();
        if (autoResolveThreshold < 0.65D || autoResolveThreshold > 0.99D) {
            throw new BusinessException(400, "autoResolveSimilarityThreshold 须在 0.65 ~ 0.99 之间");
        }

        governanceAutoResolveService.saveWorkspaceConfig(
                workspaceId, autoResolveEnabled, autoResolveThreshold);
        return getConfig(workspaceId);
    }

    /**
     * 保留主版本：建立 supersede 关系并下架重复记忆
     */
    private void executeKeepPrimaryDeprecateOthers(Long primaryKnowledgeId,
                                                   List<Long> duplicateIdList,
                                                   String workspaceId,
                                                   Long operatorId,
                                                   String memberRole,
                                                   String comment) {
        knowledgeService.getDetail(primaryKnowledgeId, workspaceId);
        for (Long duplicateId : duplicateIdList) {
            KnowledgeSupersedeRequest supersedeRequest = new KnowledgeSupersedeRequest();
            supersedeRequest.setPredecessorId(duplicateId);
            supersedeRequest.setComment(StringUtils.defaultString(comment, "治理：保留主版本并下架重复记忆"));
            graphGovernanceService.supersede(primaryKnowledgeId, supersedeRequest, workspaceId, operatorId, memberRole);
            knowledgeTimelineService.recordGovernanceDeprecate(
                    primaryKnowledgeId, duplicateId, workspaceId, operatorId, comment);
        }
    }

    /**
     * 合并 Fact：将重复 Rule 的 Fact 追加到主版本后下架副本
     */
    private void executeMergeFacts(Long primaryKnowledgeId,
                                   List<Long> duplicateIdList,
                                   String workspaceId,
                                   Long operatorId,
                                   String memberRole,
                                   String comment) {
        KnowledgeAggregate primaryAggregate = knowledgeService.getDetail(primaryKnowledgeId, workspaceId);
        if (!MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(primaryAggregate.getKnowledgeType())) {
            throw new BusinessException(400, "merge_facts 仅适用于 Rule 类型");
        }
        for (Long duplicateId : duplicateIdList) {
            KnowledgeAggregate duplicateAggregate = knowledgeService.getDetail(duplicateId, workspaceId);
            List<FactBlock> factBlockList = duplicateAggregate.getFacts();
            if (factBlockList != null && !factBlockList.isEmpty()) {
                knowledgeService.appendFacts(primaryKnowledgeId, factBlockList, workspaceId);
                knowledgeTimelineService.recordGovernanceMerge(
                        primaryKnowledgeId, duplicateId, workspaceId, operatorId, comment);
            }
            KnowledgeSupersedeRequest supersedeRequest = new KnowledgeSupersedeRequest();
            supersedeRequest.setPredecessorId(duplicateId);
            supersedeRequest.setComment(StringUtils.defaultString(comment, "治理：Fact 已合并到主版本 Rule"));
            graphGovernanceService.supersede(primaryKnowledgeId, supersedeRequest, workspaceId, operatorId, memberRole);
        }
    }

    private GovernanceIssueEntity buildIssueEntity(DuplicateGroupScanResult groupResult,
                                                   String workspaceId,
                                                   Long batchId) {
        GovernanceIssueEntity issueEntity = new GovernanceIssueEntity();
        issueEntity.setWorkspaceId(workspaceId);
        issueEntity.setIssueType(GovernanceConstants.ISSUE_TYPE_DUPLICATE);
        issueEntity.setStatus(GovernanceConstants.ISSUE_STATUS_OPEN);
        issueEntity.setPrimaryKnowledgeId(groupResult.getPrimaryKnowledgeId());
        issueEntity.setRelatedKnowledgeIds(JSONUtil.toJsonStr(groupResult.getDuplicateKnowledgeIds()));
        issueEntity.setKnowledgeType(groupResult.getKnowledgeType());
        issueEntity.setSimilarityScore(groupResult.getMaxSimilarityScore());
        issueEntity.setSuggestedAction(groupResult.getSuggestedAction());
        issueEntity.setScanBatchId(batchId);
        return issueEntity;
    }

    /**
     * 碎片聚类工单：通过 mergeConfirm 执行合并优化
     */
    private void executeMergeOptimize(GovernanceIssueEntity issueEntity,
                                      GovernanceResolveRequest resolveRequest,
                                      String workspaceId,
                                      Long operatorId,
                                      String memberRole) {
        if (resolveRequest.getMergeConfirm() == null) {
            throw new BusinessException(400, "merge_optimize 须携带 mergeConfirm 合并内容");
        }
        GovernanceMergeConfirmRequest mergeConfirm = resolveRequest.getMergeConfirm();
        if (mergeConfirm.getIssueId() == null) {
            mergeConfirm.setIssueId(issueEntity.getId());
        }
        if (mergeConfirm.getSourceKnowledgeIds() == null || mergeConfirm.getSourceKnowledgeIds().isEmpty()) {
            List<Long> sourceIds = new ArrayList<Long>();
            sourceIds.add(issueEntity.getPrimaryKnowledgeId());
            sourceIds.addAll(parseRelatedKnowledgeIds(issueEntity.getRelatedKnowledgeIds()));
            mergeConfirm.setSourceKnowledgeIds(sourceIds);
        }
        if (StringUtils.isBlank(mergeConfirm.getComment())) {
            mergeConfirm.setComment(resolveRequest.getComment());
        }
        mergeEngine.confirmMerge(mergeConfirm, workspaceId, operatorId, memberRole);
    }

    private void closeIssueAfterMerge(Long issueId, String workspaceId, Long operatorId, String comment) {
        GovernanceIssueEntity issueEntity = requireIssueInWorkspace(issueId, workspaceId);
        if (issueEntity.getStatus() != GovernanceConstants.ISSUE_STATUS_OPEN) {
            return;
        }
        issueEntity.setStatus(GovernanceConstants.ISSUE_STATUS_RESOLVED);
        issueEntity.setResolvedAction(GovernanceConstants.ACTION_MERGE_OPTIMIZE);
        issueEntity.setResolveComment(comment);
        issueEntity.setOperatorId(operatorId);
        issueEntity.setResolveTime(new Date());
        governanceIssueMapper.updateById(issueEntity);
    }

    private List<Long> resolveSourceKnowledgeIds(GovernanceMergePreviewRequest previewRequest,
                                                   String workspaceId) {
        if (previewRequest == null) {
            throw new BusinessException(400, "预览请求不能为空");
        }
        if (previewRequest.getIssueId() != null) {
            GovernanceIssueEntity issueEntity = requireIssueInWorkspace(previewRequest.getIssueId(), workspaceId);
            List<Long> sourceIds = new ArrayList<Long>();
            sourceIds.add(issueEntity.getPrimaryKnowledgeId());
            sourceIds.addAll(parseRelatedKnowledgeIds(issueEntity.getRelatedKnowledgeIds()));
            return sourceIds;
        }
        if (previewRequest.getKnowledgeIds() == null || previewRequest.getKnowledgeIds().size() < 2) {
            throw new BusinessException(400, "请至少选择 2 条记忆生成合并预览");
        }
        return previewRequest.getKnowledgeIds();
    }

    private GovernanceIssueEntity buildFragmentIssueEntity(FragmentClusterScanResult clusterResult,
                                                           String workspaceId,
                                                           Long batchId) {
        GovernanceIssueEntity issueEntity = new GovernanceIssueEntity();
        issueEntity.setWorkspaceId(workspaceId);
        issueEntity.setIssueType(GovernanceConstants.ISSUE_TYPE_FRAGMENT_CLUSTER);
        issueEntity.setStatus(GovernanceConstants.ISSUE_STATUS_OPEN);
        issueEntity.setPrimaryKnowledgeId(clusterResult.getPrimaryKnowledgeId());
        issueEntity.setRelatedKnowledgeIds(JSONUtil.toJsonStr(clusterResult.getMemberKnowledgeIds()));
        issueEntity.setKnowledgeType(clusterResult.getKnowledgeType());
        issueEntity.setSimilarityScore(clusterResult.getMaxSimilarityScore());
        issueEntity.setSuggestedAction(clusterResult.getSuggestedAction());
        issueEntity.setScanBatchId(batchId);
        return issueEntity;
    }

    private Set<String> loadOpenIssueSignatures(String workspaceId) {
        return loadOpenIssueSignatures(workspaceId, null);
    }

    private Set<String> loadOpenIssueSignatures(String workspaceId, String issueType) {
        Set<String> signatureSet = new HashSet<String>();
        LambdaQueryWrapper<GovernanceIssueEntity> queryWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        queryWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceIssueEntity::getStatus, GovernanceConstants.ISSUE_STATUS_OPEN);
        if (StringUtils.isNotBlank(issueType)) {
            queryWrapper.eq(GovernanceIssueEntity::getIssueType, issueType);
        }
        List<GovernanceIssueEntity> openIssueList = governanceIssueMapper.selectList(queryWrapper);
        for (GovernanceIssueEntity issueEntity : openIssueList) {
            List<Long> relatedIdList = parseRelatedKnowledgeIds(issueEntity.getRelatedKnowledgeIds());
            signatureSet.add(buildIssueSignature(issueEntity.getPrimaryKnowledgeId(), relatedIdList));
        }
        return signatureSet;
    }

    private String buildIssueSignature(Long primaryKnowledgeId, List<Long> duplicateIdList) {
        List<Long> sortedIdList = new ArrayList<Long>();
        sortedIdList.add(primaryKnowledgeId);
        if (duplicateIdList != null) {
            sortedIdList.addAll(duplicateIdList);
        }
        Collections.sort(sortedIdList);
        StringBuilder builder = new StringBuilder();
        for (Long knowledgeId : sortedIdList) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(knowledgeId);
        }
        return builder.toString();
    }

    private List<Long> parseRelatedKnowledgeIds(String relatedJson) {
        if (StringUtils.isBlank(relatedJson)) {
            return new ArrayList<Long>();
        }
        return JSONUtil.toList(relatedJson, Long.class);
    }

    /**
     * 构建除主版本外的全部关联记忆 ID（支持用户改选主版本时仍覆盖原 primary）
     */
    private List<Long> buildDuplicateIdListExcludingPrimary(GovernanceIssueEntity issueEntity,
                                                            Long primaryKnowledgeId) {
        Set<Long> memberIdSet = new HashSet<Long>();
        if (issueEntity.getPrimaryKnowledgeId() != null) {
            memberIdSet.add(issueEntity.getPrimaryKnowledgeId());
        }
        memberIdSet.addAll(parseRelatedKnowledgeIds(issueEntity.getRelatedKnowledgeIds()));
        if (primaryKnowledgeId == null || !memberIdSet.contains(primaryKnowledgeId)) {
            throw new BusinessException(400, "主版本须为工单关联记忆之一");
        }
        List<Long> duplicateIdList = new ArrayList<Long>();
        for (Long memberId : memberIdSet) {
            if (!memberId.equals(primaryKnowledgeId)) {
                duplicateIdList.add(memberId);
            }
        }
        return duplicateIdList;
    }

    /**
     * 校验处置动作与工单类型匹配，避免误用重复组动作处理过时/冲突工单
     */
    private void validateResolveActionForIssueType(String action, String issueType) {
        if (GovernanceConstants.ACTION_DISMISS.equals(action)) {
            return;
        }
        if (GovernanceConstants.ISSUE_TYPE_DUPLICATE.equals(issueType)) {
            if (GovernanceConstants.ACTION_KEEP_PRIMARY_DEPRECATE_OTHERS.equals(action)
                    || GovernanceConstants.ACTION_MERGE_FACTS.equals(action)) {
                return;
            }
            throw new BusinessException(400, "重复记忆工单不支持该处置动作：" + action);
        }
        if (GovernanceConstants.ISSUE_TYPE_FRAGMENT_CLUSTER.equals(issueType)
                || GovernanceConstants.ISSUE_TYPE_INCOMPLETE.equals(issueType)) {
            if (GovernanceConstants.ACTION_MERGE_OPTIMIZE.equals(action)) {
                return;
            }
            throw new BusinessException(400, "碎片/不完整工单仅支持合并优化或标记误报");
        }
        if (GovernanceConstants.ISSUE_TYPE_OUTDATED.equals(issueType)) {
            if (GovernanceConstants.ACTION_DEPRECATE.equals(action)) {
                return;
            }
            throw new BusinessException(400, "过时工单仅支持下架或标记误报");
        }
        if (GovernanceConstants.ISSUE_TYPE_CONFLICT.equals(issueType)) {
            throw new BusinessException(400, "冲突工单请标记误报或在知识详情页人工处理");
        }
        throw new BusinessException(400, "不支持的处置动作：" + action);
    }

    /**
     * 合并确认时校验源记忆与工单成员一致，防止仅合并子集却关闭工单
     */
    private void validateMergeConfirmMatchesIssue(GovernanceMergeConfirmRequest confirmRequest,
                                                  String workspaceId) {
        GovernanceIssueEntity issueEntity = requireIssueInWorkspace(confirmRequest.getIssueId(), workspaceId);
        if (issueEntity.getStatus() != GovernanceConstants.ISSUE_STATUS_OPEN) {
            throw new BusinessException(400, "工单已处理");
        }
        Set<Long> issueMemberIdSet = new HashSet<Long>();
        issueMemberIdSet.add(issueEntity.getPrimaryKnowledgeId());
        issueMemberIdSet.addAll(parseRelatedKnowledgeIds(issueEntity.getRelatedKnowledgeIds()));
        Set<Long> sourceIdSet = new HashSet<Long>();
        if (confirmRequest.getSourceKnowledgeIds() != null) {
            sourceIdSet.addAll(confirmRequest.getSourceKnowledgeIds());
        }
        if (!sourceIdSet.equals(issueMemberIdSet)) {
            throw new BusinessException(400, "合并源记忆须与工单关联记忆完全一致");
        }
    }

    private GovernanceIssueView toIssueSummaryView(GovernanceIssueEntity entity, String workspaceId) {
        GovernanceIssueView issueView = new GovernanceIssueView();
        copyIssueFields(entity, issueView);
        try {
            issueView.setPrimaryKnowledge(knowledgeService.getDetail(entity.getPrimaryKnowledgeId(), workspaceId));
        } catch (Exception exception) {
            // 主版本可能已下架，摘要仍返回工单元数据
        }
        return issueView;
    }

    private GovernanceIssueView toIssueDetailView(GovernanceIssueEntity entity, String workspaceId) {
        GovernanceIssueView issueView = toIssueSummaryView(entity, workspaceId);
        List<Long> duplicateIdList = parseRelatedKnowledgeIds(entity.getRelatedKnowledgeIds());
        List<KnowledgeAggregate> relatedList = new ArrayList<KnowledgeAggregate>();
        for (Long duplicateId : duplicateIdList) {
            try {
                relatedList.add(knowledgeService.getDetail(duplicateId, workspaceId));
            } catch (Exception exception) {
                // 跳过已不可访问的重复项
            }
        }
        issueView.setRelatedKnowledgeList(relatedList);
        return issueView;
    }

    private void copyIssueFields(GovernanceIssueEntity entity, GovernanceIssueView issueView) {
        issueView.setId(entity.getId());
        issueView.setIssueType(entity.getIssueType());
        issueView.setStatus(entity.getStatus());
        issueView.setPrimaryKnowledgeId(entity.getPrimaryKnowledgeId());
        issueView.setRelatedKnowledgeIds(parseRelatedKnowledgeIds(entity.getRelatedKnowledgeIds()));
        issueView.setKnowledgeType(entity.getKnowledgeType());
        issueView.setSimilarityScore(entity.getSimilarityScore());
        issueView.setSuggestedAction(entity.getSuggestedAction());
        issueView.setScanBatchId(entity.getScanBatchId());
        issueView.setIssueMetadataJson(entity.getIssueMetadataJson());
        issueView.setResolvedAction(entity.getResolvedAction());
        issueView.setResolveComment(entity.getResolveComment());
        issueView.setOperatorId(entity.getOperatorId());
        issueView.setCreateTime(entity.getCreateTime());
        issueView.setResolveTime(entity.getResolveTime());
    }

    private GovernanceScanBatchView toBatchView(GovernanceScanBatchEntity entity) {
        GovernanceScanBatchView batchView = new GovernanceScanBatchView();
        batchView.setId(entity.getId());
        batchView.setScanType(entity.getScanType());
        batchView.setKnowledgeType(entity.getKnowledgeType());
        batchView.setModuleFilter(entity.getModuleFilter());
        batchView.setStatus(entity.getStatus());
        batchView.setScannedCount(entity.getScannedCount());
        batchView.setIssueCount(entity.getIssueCount());
        batchView.setSimilarityThreshold(entity.getSimilarityThreshold());
        batchView.setAutoResolvedCount(entity.getAutoResolvedCount());
        batchView.setErrorMessage(entity.getErrorMessage());
        batchView.setOperatorId(entity.getOperatorId());
        batchView.setCreateTime(entity.getCreateTime());
        batchView.setFinishTime(entity.getFinishTime());
        return batchView;
    }

    private int countIssuesByStatus(String workspaceId, int status) {
        LambdaQueryWrapper<GovernanceIssueEntity> queryWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        queryWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceIssueEntity::getStatus, status);
        return governanceIssueMapper.selectCount(queryWrapper).intValue();
    }

    private int countOpenIssuesByType(String workspaceId, String issueType) {
        LambdaQueryWrapper<GovernanceIssueEntity> queryWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        queryWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceIssueEntity::getStatus, GovernanceConstants.ISSUE_STATUS_OPEN)
                .eq(GovernanceIssueEntity::getIssueType, issueType);
        return governanceIssueMapper.selectCount(queryWrapper).intValue();
    }

    private int countPublishedKnowledge(String workspaceId, String knowledgeTypeFilter, String moduleFilter) {
        LambdaQueryWrapper<KnowledgeEntity> queryWrapper = new LambdaQueryWrapper<KnowledgeEntity>();
        queryWrapper.eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                .eq(KnowledgeEntity::getLifecycleStatus, MemoryConstants.LIFECYCLE_PUBLISHED);
        if (StringUtils.isNotBlank(knowledgeTypeFilter)) {
            queryWrapper.eq(KnowledgeEntity::getKnowledgeType, knowledgeTypeFilter);
        }
        if (StringUtils.isNotBlank(moduleFilter)) {
            queryWrapper.eq(KnowledgeEntity::getModule, moduleFilter.trim());
        }
        return knowledgeMapper.selectCount(queryWrapper).intValue();
    }

    private GovernanceIssueEntity requireIssueInWorkspace(Long issueId, String workspaceId) {
        requireWorkspaceId(workspaceId);
        GovernanceIssueEntity entity = governanceIssueMapper.selectById(issueId);
        if (entity == null) {
            throw new BusinessException(404, "治理工单不存在");
        }
        if (!workspaceId.equals(entity.getWorkspaceId())) {
            throw new BusinessException(403, "无权访问该治理工单");
        }
        return entity;
    }

    private void requireWorkspaceId(String workspaceId) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "当前未选择工作空间");
        }
    }

    private String resolveScanType(GovernanceScanRequest scanRequest) {
        if (scanRequest == null || StringUtils.isBlank(scanRequest.getScanType())) {
            return GovernanceConstants.SCAN_TYPE_FULL;
        }
        return scanRequest.getScanType().trim();
    }

    private double resolveThreshold(GovernanceScanRequest scanRequest) {
        if (scanRequest == null || scanRequest.getSimilarityThreshold() == null) {
            return GovernanceConstants.DEFAULT_SIMILARITY_THRESHOLD;
        }
        double threshold = scanRequest.getSimilarityThreshold();
        if (threshold < 0.15D || threshold > 0.99D) {
            throw new BusinessException(400, "similarityThreshold 须在 0.15 ~ 0.99 之间");
        }
        return threshold;
    }

    private double resolveFragmentThreshold(GovernanceScanRequest scanRequest) {
        if (scanRequest == null || scanRequest.getSimilarityThreshold() == null) {
            return GovernanceConstants.DEFAULT_FRAGMENT_SIMILARITY_THRESHOLD;
        }
        return resolveThreshold(scanRequest);
    }

    private String resolveScanMode(GovernanceScanRequest scanRequest) {
        if (scanRequest == null) {
            return GovernanceConstants.SCAN_TYPE_FULL;
        }
        if (StringUtils.isNotBlank(scanRequest.getScanMode())) {
            return scanRequest.getScanMode().trim();
        }
        if (GovernanceConstants.SCAN_TYPE_FRAGMENT.equals(scanRequest.getScanType())) {
            return GovernanceConstants.SCAN_TYPE_FRAGMENT;
        }
        if (GovernanceConstants.SCAN_TYPE_VALIDATE.equals(scanRequest.getScanType())) {
            return GovernanceConstants.SCAN_TYPE_VALIDATE;
        }
        return resolveScanType(scanRequest);
    }

    private GovernanceIssueEntity buildValidationIssueEntity(ValidationScanResult validationResult,
                                                             String workspaceId,
                                                             Long batchId) {
        GovernanceIssueEntity issueEntity = new GovernanceIssueEntity();
        issueEntity.setWorkspaceId(workspaceId);
        issueEntity.setIssueType(validationResult.getIssueType());
        issueEntity.setStatus(GovernanceConstants.ISSUE_STATUS_OPEN);
        issueEntity.setPrimaryKnowledgeId(validationResult.getPrimaryKnowledgeId());
        issueEntity.setRelatedKnowledgeIds(JSONUtil.toJsonStr(validationResult.getRelatedKnowledgeIds()));
        issueEntity.setKnowledgeType(validationResult.getKnowledgeType());
        issueEntity.setSimilarityScore(validationResult.getScore());
        issueEntity.setSuggestedAction(validationResult.getSuggestedAction());
        issueEntity.setScanBatchId(batchId);
        issueEntity.setIssueMetadataJson(validationResult.getMetadataJson());
        return issueEntity;
    }

    private Set<String> loadOpenValidationSignatures(String workspaceId) {
        Set<String> signatureSet = new HashSet<String>();
        LambdaQueryWrapper<GovernanceIssueEntity> queryWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        queryWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceIssueEntity::getStatus, GovernanceConstants.ISSUE_STATUS_OPEN)
                .in(GovernanceIssueEntity::getIssueType,
                        GovernanceConstants.ISSUE_TYPE_INCOMPLETE,
                        GovernanceConstants.ISSUE_TYPE_CONFLICT);
        List<GovernanceIssueEntity> openIssueList = governanceIssueMapper.selectList(queryWrapper);
        for (GovernanceIssueEntity issueEntity : openIssueList) {
            signatureSet.add(buildValidationSignature(issueEntity));
        }
        return signatureSet;
    }

    private String buildValidationSignature(ValidationScanResult validationResult) {
        return validationResult.getIssueType() + ":"
                + validationResult.getPrimaryKnowledgeId() + ":"
                + StringUtils.join(validationResult.getRelatedKnowledgeIds(), ',');
    }

    private String buildValidationSignature(GovernanceIssueEntity issueEntity) {
        List<Long> relatedIdList = parseRelatedKnowledgeIds(issueEntity.getRelatedKnowledgeIds());
        return issueEntity.getIssueType() + ":"
                + issueEntity.getPrimaryKnowledgeId() + ":"
                + StringUtils.join(relatedIdList, ',');
    }

    private Date findLastScanFinishTime(String workspaceId, String scanType) {
        LambdaQueryWrapper<GovernanceScanBatchEntity> batchWrapper = new LambdaQueryWrapper<GovernanceScanBatchEntity>();
        batchWrapper.eq(GovernanceScanBatchEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceScanBatchEntity::getStatus, GovernanceConstants.SCAN_STATUS_DONE)
                .eq(GovernanceScanBatchEntity::getScanType, scanType)
                .orderByDesc(GovernanceScanBatchEntity::getFinishTime)
                .last("LIMIT 1");
        GovernanceScanBatchEntity latestBatch = governanceScanBatchMapper.selectOne(batchWrapper);
        return latestBatch == null ? null : latestBatch.getFinishTime();
    }

    private int countAutoResolvedIssues(String workspaceId) {
        LambdaQueryWrapper<GovernanceIssueEntity> queryWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        queryWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceIssueEntity::getStatus, GovernanceConstants.ISSUE_STATUS_RESOLVED)
                .like(GovernanceIssueEntity::getResolveComment, GovernanceConstants.AUTO_RESOLVE_COMMENT_PREFIX);
        return governanceIssueMapper.selectCount(queryWrapper).intValue();
    }

    private GovernanceFunnelView buildFunnelStats(String workspaceId) {
        GovernanceFunnelView funnelView = new GovernanceFunnelView();

        LambdaQueryWrapper<GovernanceScanBatchEntity> batchWrapper = new LambdaQueryWrapper<GovernanceScanBatchEntity>();
        batchWrapper.eq(GovernanceScanBatchEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceScanBatchEntity::getStatus, GovernanceConstants.SCAN_STATUS_DONE);
        funnelView.setTotalScanBatchCount(governanceScanBatchMapper.selectCount(batchWrapper).intValue());

        LambdaQueryWrapper<GovernanceIssueEntity> allIssueWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        allIssueWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId);
        funnelView.setTotalIssueCreatedCount(governanceIssueMapper.selectCount(allIssueWrapper).intValue());

        funnelView.setTotalIssueResolvedCount(countIssuesByStatus(
                workspaceId, GovernanceConstants.ISSUE_STATUS_RESOLVED));
        funnelView.setTotalIssueDismissedCount(countIssuesByStatus(
                workspaceId, GovernanceConstants.ISSUE_STATUS_DISMISSED));
        funnelView.setTotalAutoResolvedCount(countAutoResolvedIssues(workspaceId));
        return funnelView;
    }
}
