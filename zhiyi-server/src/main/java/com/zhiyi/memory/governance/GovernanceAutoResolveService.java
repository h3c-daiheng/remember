package com.zhiyi.memory.governance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.config.GovernanceProperties;
import com.zhiyi.dao.WorkspaceGovernanceConfigMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.domain.entity.WorkspaceGovernanceConfigEntity;
import com.zhiyi.domain.vo.KnowledgeSupersedeRequest;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.dao.GovernanceIssueMapper;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.entity.GovernanceIssueEntity;
import com.zhiyi.memory.graph.GraphGovernanceService;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.service.AgentCreatorResolver;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.workspace.WorkspaceMemberRole;
import cn.hutool.json.JSONUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 高置信重复组自动处置：按工作空间配置在扫描后自动合并或下架副本
 */
@Component
public class GovernanceAutoResolveService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceAutoResolveService.class);

    private final GovernanceIssueMapper governanceIssueMapper;
    private final WorkspaceGovernanceConfigMapper workspaceGovernanceConfigMapper;
    private final WorkspaceMapper workspaceMapper;
    private final GovernanceProperties governanceProperties;
    private final KnowledgeService knowledgeService;
    private final GraphGovernanceService graphGovernanceService;
    private final KnowledgeTimelineService knowledgeTimelineService;
    private final AgentCreatorResolver agentCreatorResolver;

    public GovernanceAutoResolveService(GovernanceIssueMapper governanceIssueMapper,
                                        WorkspaceGovernanceConfigMapper workspaceGovernanceConfigMapper,
                                        WorkspaceMapper workspaceMapper,
                                        GovernanceProperties governanceProperties,
                                        KnowledgeService knowledgeService,
                                        GraphGovernanceService graphGovernanceService,
                                        KnowledgeTimelineService knowledgeTimelineService,
                                        AgentCreatorResolver agentCreatorResolver) {
        this.governanceIssueMapper = governanceIssueMapper;
        this.workspaceGovernanceConfigMapper = workspaceGovernanceConfigMapper;
        this.workspaceMapper = workspaceMapper;
        this.governanceProperties = governanceProperties;
        this.knowledgeService = knowledgeService;
        this.graphGovernanceService = graphGovernanceService;
        this.knowledgeTimelineService = knowledgeTimelineService;
        this.agentCreatorResolver = agentCreatorResolver;
    }

    /**
     * 对指定批次产出的重复工单尝试自动处置
     *
     * @return 自动处置成功数量
     */
    public int autoResolveDuplicateIssues(String workspaceId, Long scanBatchId) {
        GovernanceWorkspaceConfig workspaceConfig = loadWorkspaceConfig(workspaceId);
        if (!workspaceConfig.isAutoResolveEnabled()) {
            return 0;
        }

        LambdaQueryWrapper<GovernanceIssueEntity> queryWrapper = new LambdaQueryWrapper<GovernanceIssueEntity>();
        queryWrapper.eq(GovernanceIssueEntity::getWorkspaceId, workspaceId)
                .eq(GovernanceIssueEntity::getScanBatchId, scanBatchId)
                .eq(GovernanceIssueEntity::getIssueType, GovernanceConstants.ISSUE_TYPE_DUPLICATE)
                .eq(GovernanceIssueEntity::getStatus, GovernanceConstants.ISSUE_STATUS_OPEN);
        List<GovernanceIssueEntity> issueEntityList = governanceIssueMapper.selectList(queryWrapper);
        if (issueEntityList.isEmpty()) {
            return 0;
        }

        Long operatorId = agentCreatorResolver.resolveWorkspaceCreatorId(workspaceId);
        String memberRole = WorkspaceMemberRole.OWNER;
        double threshold = workspaceConfig.getAutoResolveSimilarityThreshold();
        int resolvedCount = 0;

        for (GovernanceIssueEntity issueEntity : issueEntityList) {
            if (issueEntity.getSimilarityScore() == null
                    || issueEntity.getSimilarityScore().doubleValue() < threshold) {
                continue;
            }
            try {
                autoResolveSingleIssue(issueEntity, workspaceId, operatorId, memberRole);
                resolvedCount++;
            } catch (Exception exception) {
                log.warn("自动处置治理工单失败 issueId={} reason={}", issueEntity.getId(), exception.getMessage());
            }
        }
        log.info("治理自动处置完成 workspaceId={} batchId={} resolvedCount={}",
                workspaceId, scanBatchId, resolvedCount);
        return resolvedCount;
    }

    /**
     * 判断单条重复工单是否满足自动处置条件
     */
    public boolean canAutoResolve(GovernanceIssueEntity issueEntity, GovernanceWorkspaceConfig workspaceConfig) {
        if (issueEntity == null || workspaceConfig == null || !workspaceConfig.isAutoResolveEnabled()) {
            return false;
        }
        if (!GovernanceConstants.ISSUE_TYPE_DUPLICATE.equals(issueEntity.getIssueType())) {
            return false;
        }
        if (issueEntity.getSimilarityScore() == null) {
            return false;
        }
        return issueEntity.getSimilarityScore().doubleValue() >= workspaceConfig.getAutoResolveSimilarityThreshold();
    }

    /**
     * 读取工作空间治理配置：优先独立配置表，兼容历史 workspace 表字段
     */
    public GovernanceWorkspaceConfig loadWorkspaceConfig(String workspaceId) {
        String configJson = resolveGovernanceConfigJson(workspaceId);
        return GovernanceWorkspaceConfig.merge(
                configJson,
                governanceProperties.isAutoResolveEnabled(),
                governanceProperties.getAutoResolveSimilarityThreshold());
    }

    /**
     * 保存工作空间治理配置到独立表，避免依赖本地 workspace 种子数据
     */
    public void saveWorkspaceConfig(String workspaceId, boolean autoResolveEnabled, double autoResolveSimilarityThreshold) {
        String configJson = GovernanceWorkspaceConfig.toJson(autoResolveEnabled, autoResolveSimilarityThreshold);
        Date now = new Date();
        WorkspaceGovernanceConfigEntity configEntity = workspaceGovernanceConfigMapper.selectById(workspaceId);
        if (configEntity == null) {
            configEntity = new WorkspaceGovernanceConfigEntity();
            configEntity.setWorkspaceId(workspaceId);
            configEntity.setGovernanceConfigJson(configJson);
            configEntity.setUpdateTime(now);
            workspaceGovernanceConfigMapper.insert(configEntity);
            return;
        }
        configEntity.setGovernanceConfigJson(configJson);
        configEntity.setUpdateTime(now);
        workspaceGovernanceConfigMapper.updateById(configEntity);
    }

    /**
     * 解析治理配置 JSON：新表优先，回退 workspace 历史字段
     */
    private String resolveGovernanceConfigJson(String workspaceId) {
        WorkspaceGovernanceConfigEntity configEntity = workspaceGovernanceConfigMapper.selectById(workspaceId);
        if (configEntity != null && StringUtils.isNotBlank(configEntity.getGovernanceConfigJson())) {
            return configEntity.getGovernanceConfigJson();
        }
        WorkspaceEntity workspaceEntity = workspaceMapper.selectById(workspaceId);
        return workspaceEntity == null ? null : workspaceEntity.getGovernanceConfigJson();
    }

    private void autoResolveSingleIssue(GovernanceIssueEntity issueEntity,
                                        String workspaceId,
                                        Long operatorId,
                                        String memberRole) {
        String suggestedAction = issueEntity.getSuggestedAction();
        Long primaryKnowledgeId = issueEntity.getPrimaryKnowledgeId();
        List<Long> duplicateIdList = buildAutoResolveDuplicateIdList(issueEntity, primaryKnowledgeId);
        String autoComment = GovernanceConstants.AUTO_RESOLVE_COMMENT_PREFIX + " 高置信重复自动处置";

        if (GovernanceConstants.ACTION_MERGE_FACTS.equals(suggestedAction)) {
            executeMergeFacts(primaryKnowledgeId, duplicateIdList, workspaceId, operatorId, memberRole, autoComment);
            issueEntity.setResolvedAction(GovernanceConstants.ACTION_MERGE_FACTS);
        } else {
            executeKeepPrimaryDeprecateOthers(primaryKnowledgeId, duplicateIdList, workspaceId, operatorId,
                    memberRole, autoComment);
            issueEntity.setResolvedAction(GovernanceConstants.ACTION_KEEP_PRIMARY_DEPRECATE_OTHERS);
        }

        issueEntity.setStatus(GovernanceConstants.ISSUE_STATUS_RESOLVED);
        issueEntity.setResolveComment(autoComment);
        issueEntity.setOperatorId(operatorId);
        issueEntity.setResolveTime(new Date());
        governanceIssueMapper.updateById(issueEntity);
    }

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
            supersedeRequest.setComment(comment);
            graphGovernanceService.supersede(primaryKnowledgeId, supersedeRequest, workspaceId, operatorId, memberRole);
            knowledgeTimelineService.recordGovernanceDeprecate(
                    primaryKnowledgeId, duplicateId, workspaceId, operatorId, comment);
        }
    }

    private void executeMergeFacts(Long primaryKnowledgeId,
                                   List<Long> duplicateIdList,
                                   String workspaceId,
                                   Long operatorId,
                                   String memberRole,
                                   String comment) {
        KnowledgeAggregate primaryAggregate = knowledgeService.getDetail(primaryKnowledgeId, workspaceId);
        if (!MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(primaryAggregate.getKnowledgeType())) {
            executeKeepPrimaryDeprecateOthers(primaryKnowledgeId, duplicateIdList, workspaceId, operatorId,
                    memberRole, comment);
            return;
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
            supersedeRequest.setComment(comment);
            graphGovernanceService.supersede(primaryKnowledgeId, supersedeRequest, workspaceId, operatorId, memberRole);
        }
    }

    private List<Long> parseRelatedKnowledgeIds(String relatedJson) {
        if (StringUtils.isBlank(relatedJson)) {
            return new ArrayList<Long>();
        }
        return JSONUtil.toList(relatedJson, Long.class);
    }

    /**
     * 自动处置时构建待下架副本列表（含 primary + related 全集）
     */
    private List<Long> buildAutoResolveDuplicateIdList(GovernanceIssueEntity issueEntity,
                                                       Long primaryKnowledgeId) {
        Set<Long> memberIdSet = new HashSet<Long>();
        if (issueEntity.getPrimaryKnowledgeId() != null) {
            memberIdSet.add(issueEntity.getPrimaryKnowledgeId());
        }
        memberIdSet.addAll(parseRelatedKnowledgeIds(issueEntity.getRelatedKnowledgeIds()));
        List<Long> duplicateIdList = new ArrayList<Long>();
        for (Long memberId : memberIdSet) {
            if (primaryKnowledgeId == null || !memberId.equals(primaryKnowledgeId)) {
                duplicateIdList.add(memberId);
            }
        }
        return duplicateIdList;
    }
}
