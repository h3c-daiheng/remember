package com.zhiyi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.dao.ApiKeyMapper;
import com.zhiyi.dao.SysUserMapper;
import com.zhiyi.dao.UsageDailyMapper;
import com.zhiyi.dao.WorkspaceGovernanceConfigMapper;
import com.zhiyi.dao.WorkspaceMapper;
import com.zhiyi.dao.WorkspaceMemberMapper;
import com.zhiyi.domain.entity.ApiKeyEntity;
import com.zhiyi.domain.entity.UsageDailyEntity;
import com.zhiyi.domain.entity.WorkspaceEntity;
import com.zhiyi.domain.entity.WorkspaceGovernanceConfigEntity;
import com.zhiyi.domain.entity.WorkspaceMemberEntity;
import com.zhiyi.memory.dao.CaptureAiReviewMapper;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.GovernanceIssueMapper;
import com.zhiyi.memory.dao.GovernanceScanBatchMapper;
import com.zhiyi.memory.dao.KnowledgeArtifactMapper;
import com.zhiyi.memory.dao.KnowledgeFactMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeRelationMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.KnowledgeTimelineMapper;
import com.zhiyi.memory.dao.KnowledgeVectorRefMapper;
import com.zhiyi.memory.dao.MemoryFeedbackMapper;
import com.zhiyi.memory.dao.MemoryOperationLogMapper;
import com.zhiyi.memory.dao.SystemEventMapper;
import com.zhiyi.memory.entity.CaptureAiReviewEntity;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.entity.GovernanceIssueEntity;
import com.zhiyi.memory.entity.GovernanceScanBatchEntity;
import com.zhiyi.memory.entity.KnowledgeArtifactEntity;
import com.zhiyi.memory.entity.KnowledgeFactEntity;
import com.zhiyi.memory.entity.KnowledgeRelationEntity;
import com.zhiyi.memory.entity.KnowledgeTimelineEntity;
import com.zhiyi.memory.entity.KnowledgeVectorRefEntity;
import com.zhiyi.memory.entity.MemoryFeedbackEntity;
import com.zhiyi.memory.entity.MemoryOperationLogEntity;
import com.zhiyi.memory.entity.SystemEventEntity;
import com.zhiyi.workspace.WorkspaceMemberRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作空间管理服务:承载删除工作空间的级联清理(无 DB 外键,应用层手动清理)。
 */
@Service
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final WorkspaceGovernanceConfigMapper workspaceGovernanceConfigMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final UsageDailyMapper usageDailyMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeFactMapper knowledgeFactMapper;
    private final KnowledgeArtifactMapper knowledgeArtifactMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;
    private final KnowledgeVectorRefMapper knowledgeVectorRefMapper;
    private final KnowledgeRelationMapper knowledgeRelationMapper;
    private final KnowledgeTimelineMapper knowledgeTimelineMapper;
    private final MemoryFeedbackMapper memoryFeedbackMapper;
    private final MemoryOperationLogMapper memoryOperationLogMapper;
    private final CaptureDraftMapper captureDraftMapper;
    private final CaptureAiReviewMapper captureAiReviewMapper;
    private final SystemEventMapper systemEventMapper;
    private final GovernanceIssueMapper governanceIssueMapper;
    private final GovernanceScanBatchMapper governanceScanBatchMapper;
    private final SysUserMapper sysUserMapper;

    public WorkspaceService(WorkspaceMapper workspaceMapper,
                            WorkspaceMemberMapper workspaceMemberMapper,
                            WorkspaceGovernanceConfigMapper workspaceGovernanceConfigMapper,
                            ApiKeyMapper apiKeyMapper,
                            UsageDailyMapper usageDailyMapper,
                            KnowledgeMapper knowledgeMapper,
                            KnowledgeFactMapper knowledgeFactMapper,
                            KnowledgeArtifactMapper knowledgeArtifactMapper,
                            KnowledgeTagMapper knowledgeTagMapper,
                            KnowledgeVectorRefMapper knowledgeVectorRefMapper,
                            KnowledgeRelationMapper knowledgeRelationMapper,
                            KnowledgeTimelineMapper knowledgeTimelineMapper,
                            MemoryFeedbackMapper memoryFeedbackMapper,
                            MemoryOperationLogMapper memoryOperationLogMapper,
                            CaptureDraftMapper captureDraftMapper,
                            CaptureAiReviewMapper captureAiReviewMapper,
                            SystemEventMapper systemEventMapper,
                            GovernanceIssueMapper governanceIssueMapper,
                            GovernanceScanBatchMapper governanceScanBatchMapper,
                            SysUserMapper sysUserMapper) {
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.workspaceGovernanceConfigMapper = workspaceGovernanceConfigMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.usageDailyMapper = usageDailyMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeFactMapper = knowledgeFactMapper;
        this.knowledgeArtifactMapper = knowledgeArtifactMapper;
        this.knowledgeTagMapper = knowledgeTagMapper;
        this.knowledgeVectorRefMapper = knowledgeVectorRefMapper;
        this.knowledgeRelationMapper = knowledgeRelationMapper;
        this.knowledgeTimelineMapper = knowledgeTimelineMapper;
        this.memoryFeedbackMapper = memoryFeedbackMapper;
        this.memoryOperationLogMapper = memoryOperationLogMapper;
        this.captureDraftMapper = captureDraftMapper;
        this.captureAiReviewMapper = captureAiReviewMapper;
        this.systemEventMapper = systemEventMapper;
        this.governanceIssueMapper = governanceIssueMapper;
        this.governanceScanBatchMapper = governanceScanBatchMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteWorkspace(String workspaceId, Long userId, String memberRole, String confirmName) {
        if (StringUtils.isBlank(workspaceId)) {
            throw new BusinessException(400, "工作空间 ID 不能为空");
        }
        if (!WorkspaceMemberRole.OWNER.equals(memberRole)) {
            throw new BusinessException(403, "仅工作空间 owner 可删除工作空间");
        }
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw new BusinessException(404, "工作空间不存在");
        }
        if (!workspace.getWorkspaceName().equals(confirmName)) {
            throw new BusinessException(400, "输入的名称与工作空间名称不符");
        }

        // 1. 治理工单 / 批次
        governanceIssueMapper.delete(byWorkspace(GovernanceIssueEntity::getWorkspaceId, workspaceId));
        governanceScanBatchMapper.delete(byWorkspace(GovernanceScanBatchEntity::getWorkspaceId, workspaceId));
        // 2. capture_ai_review / capture_draft / system_event
        captureAiReviewMapper.delete(byWorkspace(CaptureAiReviewEntity::getWorkspaceId, workspaceId));
        captureDraftMapper.delete(byWorkspace(CaptureDraftEntity::getWorkspaceId, workspaceId));
        systemEventMapper.delete(byWorkspace(SystemEventEntity::getWorkspaceId, workspaceId));
        // 3. 知识关系边 / 时间线(按 workspace_id)
        knowledgeRelationMapper.delete(byWorkspace(KnowledgeRelationEntity::getWorkspaceId, workspaceId));
        knowledgeTimelineMapper.delete(byWorkspace(KnowledgeTimelineEntity::getWorkspaceId, workspaceId));
        // 4. 知识子表(按 knowledge_id,需先查 id 或用子查询)
        knowledgeTagMapper.deleteByWorkspace(workspaceId);
        List<Long> knowledgeIds = knowledgeMapper.selectIdsByWorkspace(workspaceId);
        if (knowledgeIds != null && !knowledgeIds.isEmpty()) {
            knowledgeFactMapper.delete(inKnowledge(KnowledgeFactEntity::getKnowledgeId, knowledgeIds));
            knowledgeArtifactMapper.delete(inKnowledge(KnowledgeArtifactEntity::getKnowledgeId, knowledgeIds));
            knowledgeVectorRefMapper.delete(inKnowledge(KnowledgeVectorRefEntity::getKnowledgeId, knowledgeIds));
            memoryFeedbackMapper.delete(inKnowledge(MemoryFeedbackEntity::getKnowledgeId, knowledgeIds));
        }
        // 5. knowledge 主表物理删
        knowledgeMapper.deleteByWorkspacePhysical(workspaceId);
        // 6. 操作日志 / 用量 / API Key / 治理配置 / 成员
        memoryOperationLogMapper.delete(byWorkspace(MemoryOperationLogEntity::getWorkspaceId, workspaceId));
        usageDailyMapper.delete(byWorkspace(UsageDailyEntity::getWorkspaceId, workspaceId));
        apiKeyMapper.delete(byWorkspace(ApiKeyEntity::getWorkspaceId, workspaceId));
        workspaceGovernanceConfigMapper.delete(byWorkspace(WorkspaceGovernanceConfigEntity::getWorkspaceId, workspaceId));
        workspaceMemberMapper.delete(byWorkspace(WorkspaceMemberEntity::getWorkspaceId, workspaceId));
        // 7. sys_user.last_workspace_id 置空 + 工作空间物理删
        sysUserMapper.clearLastWorkspace(workspaceId);
        workspaceMapper.deleteById(workspaceId);
    }

    private <T> LambdaQueryWrapper<T> byWorkspace(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column, String workspaceId) {
        return new LambdaQueryWrapper<T>().eq(column, workspaceId);
    }

    private <T> LambdaQueryWrapper<T> inKnowledge(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column, List<Long> knowledgeIds) {
        return new LambdaQueryWrapper<T>().in(column, knowledgeIds);
    }
}
