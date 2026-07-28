package com.zhiyi.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.domain.vo.LoginUserVO;
import com.zhiyi.domain.vo.WorkspaceContextOptionsVO;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.dao.CaptureDraftMapper;
import com.zhiyi.memory.dao.KnowledgeMapper;
import com.zhiyi.memory.dao.KnowledgeTagMapper;
import com.zhiyi.memory.dao.MemoryOperationLogMapper;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.domain.KnowledgeTagRow;
import com.zhiyi.memory.entity.CaptureDraftEntity;
import com.zhiyi.memory.entity.KnowledgeEntity;
import com.zhiyi.memory.entity.MemoryOperationLogEntity;
import com.zhiyi.memory.util.MemoryJsonUtil;
import com.zhiyi.workspace.WorkspaceAccessGuard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 工作空间 Recall 上下文字段可选值聚合：从 knowledge、待审 capture_draft、memory_operation_log 去重
 */
@Service
public class WorkspaceContextOptionsService {

    private static final Set<String> CONTEXT_LOG_OPERATIONS = new HashSet<String>(
            Arrays.asList("recall", "search", "remember"));

    /** 操作日志取样上限，避免大 Workspace 全表扫描 */
    private static final int OPERATION_LOG_SAMPLE_LIMIT = 500;

    private final ContextNormalizer contextNormalizer;
    private final KnowledgeMapper knowledgeMapper;
    private final CaptureDraftMapper captureDraftMapper;
    private final MemoryOperationLogMapper memoryOperationLogMapper;
    private final KnowledgeTagMapper knowledgeTagMapper;

    public WorkspaceContextOptionsService(ContextNormalizer contextNormalizer,
                                          KnowledgeMapper knowledgeMapper,
                                          CaptureDraftMapper captureDraftMapper,
                                          MemoryOperationLogMapper memoryOperationLogMapper,
                                          KnowledgeTagMapper knowledgeTagMapper) {
        this.contextNormalizer = contextNormalizer;
        this.knowledgeMapper = knowledgeMapper;
        this.captureDraftMapper = captureDraftMapper;
        this.memoryOperationLogMapper = memoryOperationLogMapper;
        this.knowledgeTagMapper = knowledgeTagMapper;
    }

    /**
     * 获取当前工作空间内仓库 / 项目 / 模块 / 标签可选值
     */
    public WorkspaceContextOptionsVO getOptions(LoginUserVO loginUser, String workspaceId) {
        requireMemberContext(loginUser, workspaceId);

        Set<String> repositorySet = new TreeSet<String>();
        Set<String> projectSet = new TreeSet<String>();
        Set<String> moduleSet = new TreeSet<String>();
        Set<String> tagSet = new TreeSet<String>();

        collectFromKnowledge(workspaceId, repositorySet, projectSet, moduleSet);
        collectFromPendingCaptureDrafts(workspaceId, repositorySet, projectSet, moduleSet);
        collectFromOperationLogs(workspaceId, repositorySet, projectSet, moduleSet);
        collectTags(workspaceId, tagSet);

        WorkspaceContextOptionsVO optionsView = new WorkspaceContextOptionsVO();
        optionsView.setRepositories(toList(repositorySet));
        optionsView.setProjects(toList(projectSet));
        optionsView.setModules(toList(moduleSet));
        optionsView.setTags(toList(tagSet));
        return optionsView;
    }

    private void collectFromKnowledge(String workspaceId, Set<String> repositorySet,
                                      Set<String> projectSet, Set<String> moduleSet) {
        List<KnowledgeEntity> knowledgeEntityList = knowledgeMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .eq(KnowledgeEntity::getWorkspaceId, workspaceId)
                        .eq(KnowledgeEntity::getDeleted, 0)
                        .select(KnowledgeEntity::getProject,
                                KnowledgeEntity::getModule,
                                KnowledgeEntity::getRepository));
        for (KnowledgeEntity knowledgeEntity : knowledgeEntityList) {
            addContextValues(knowledgeEntity.getRepository(), knowledgeEntity.getProject(),
                    knowledgeEntity.getModule(), repositorySet, projectSet, moduleSet);
        }
    }

    /**
     * 仅聚合待审草稿：已采纳内容会进入 knowledge 表，避免重复解析全部 draft_json
     */
    private void collectFromPendingCaptureDrafts(String workspaceId, Set<String> repositorySet,
                                                 Set<String> projectSet, Set<String> moduleSet) {
        List<CaptureDraftEntity> draftEntityList = captureDraftMapper.selectList(
                new LambdaQueryWrapper<CaptureDraftEntity>()
                        .eq(CaptureDraftEntity::getWorkspaceId, workspaceId)
                        .eq(CaptureDraftEntity::getReviewStatus, MemoryConstants.REVIEW_PENDING)
                        .select(CaptureDraftEntity::getDraftJson));
        for (CaptureDraftEntity draftEntity : draftEntityList) {
            KnowledgeDraftContent draftContent = MemoryJsonUtil.parseDraftContent(draftEntity.getDraftJson());
            addContextValues(draftContent.getRepository(), draftContent.getProject(),
                    draftContent.getModule(), repositorySet, projectSet, moduleSet);
        }
    }

    private void collectFromOperationLogs(String workspaceId, Set<String> repositorySet,
                                          Set<String> projectSet, Set<String> moduleSet) {
        List<MemoryOperationLogEntity> logEntityList = memoryOperationLogMapper.selectList(
                new LambdaQueryWrapper<MemoryOperationLogEntity>()
                        .eq(MemoryOperationLogEntity::getWorkspaceId, workspaceId)
                        .eq(MemoryOperationLogEntity::getSuccess, 1)
                        .in(MemoryOperationLogEntity::getOperation, CONTEXT_LOG_OPERATIONS)
                        .isNotNull(MemoryOperationLogEntity::getRequestJson)
                        .select(MemoryOperationLogEntity::getRequestJson)
                        .orderByDesc(MemoryOperationLogEntity::getCreateTime)
                        .last("LIMIT " + OPERATION_LOG_SAMPLE_LIMIT));
        for (MemoryOperationLogEntity logEntity : logEntityList) {
            if (!JSONUtil.isTypeJSON(logEntity.getRequestJson())) {
                continue;
            }
            JSONObject requestObject = JSONUtil.parseObj(logEntity.getRequestJson());
            addContextValues(requestObject.getStr("repository"), requestObject.getStr("project"),
                    requestObject.getStr("module"), repositorySet, projectSet, moduleSet);
        }
    }

    private void collectTags(String workspaceId, Set<String> tagSet) {
        List<KnowledgeTagRow> tagRowList = knowledgeTagMapper.selectTagRowsByWorkspace(workspaceId);
        for (KnowledgeTagRow tagRow : tagRowList) {
            addTextOption(tagSet, tagRow.getTagName());
        }
    }

    private void addContextValues(String repositoryValue, String projectValue, String moduleValue,
                                  Set<String> repositorySet, Set<String> projectSet,
                                  Set<String> moduleSet) {
        addRepositoryOption(repositorySet, repositoryValue);
        addTextOption(projectSet, projectValue);
        addTextOption(moduleSet, moduleValue);
    }

    private void addRepositoryOption(Set<String> optionSet, String rawValue) {
        String normalizedValue = contextNormalizer.normalizeRepositoryName(rawValue);
        if (normalizedValue != null) {
            optionSet.add(normalizedValue);
        }
    }

    private void addTextOption(Set<String> optionSet, String rawValue) {
        String normalizedValue = contextNormalizer.normalizeTextValue(rawValue);
        if (normalizedValue != null) {
            optionSet.add(normalizedValue);
        }
    }

    private List<String> toList(Set<String> optionSet) {
        return new ArrayList<String>(optionSet);
    }

    /**
     * 校验路径 workspaceId 为当前登录激活空间，防止跨空间读取上下文选项
     */
    private void requireMemberContext(LoginUserVO loginUser, String workspaceId) {
        WorkspaceAccessGuard.requireCurrentWorkspaceMember(loginUser, workspaceId);
    }
}

