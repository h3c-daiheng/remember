package com.zhiyi.memory.context;

import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.domain.KnowledgeSaveRequest;
import com.zhiyi.memory.domain.RecallContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Context Engine：Recall 时补全上下文；入库前规范化 project / module / repository
 */
@Component
public class ContextNormalizer {

    /**
     * 规范化 RecallContext，补全缺失的 project 与 module
     */
    public RecallContext normalize(RecallContext recallContext) {
        RecallContext normalized = copyContext(recallContext);
        normalized.setRepository(normalizeRepositoryName(normalized.getRepository()));
        normalized.setProject(normalizeTextValue(normalized.getProject()));
        normalized.setModule(normalizeTextValue(normalized.getModule()));
        if (StringUtils.isBlank(normalized.getProject()) && StringUtils.isNotBlank(normalized.getRepository())) {
            normalized.setProject(extractProjectFromRepository(normalized.getRepository()));
        }
        if (StringUtils.isBlank(normalized.getModule()) && StringUtils.isNotBlank(normalized.getCurrentFile())) {
            normalized.setModule(extractModuleFromFile(normalized.getCurrentFile()));
        }
        if (normalized.getLimit() == null || normalized.getLimit() <= 0) {
            normalized.setLimit(10);
        }
        if (normalized.getGraphDepth() == null || normalized.getGraphDepth() <= 0) {
            normalized.setGraphDepth(2);
        }
        if (normalized.getGraphDepth() > 3) {
            normalized.setGraphDepth(3);
        }
        return normalized;
    }

    /**
     * 知识入库前规范化上下文字段，避免空格与仓库路径写法不一致导致统计分裂
     */
    public void normalizeSaveRequest(KnowledgeSaveRequest saveRequest) {
        if (saveRequest == null) {
            return;
        }
        saveRequest.setRepository(normalizeRepositoryName(saveRequest.getRepository()));
        saveRequest.setProject(normalizeTextValue(saveRequest.getProject()));
        saveRequest.setModule(normalizeTextValue(saveRequest.getModule()));
        saveRequest.setLanguage(normalizeTextValue(saveRequest.getLanguage()));
        saveRequest.setFramework(normalizeTextValue(saveRequest.getFramework()));
    }

    /**
     * Capture 草稿入库前规范化上下文字段
     */
    public void normalizeDraftContent(KnowledgeDraftContent draftContent) {
        if (draftContent == null) {
            return;
        }
        draftContent.setRepository(normalizeRepositoryName(draftContent.getRepository()));
        draftContent.setProject(normalizeTextValue(draftContent.getProject()));
        draftContent.setModule(normalizeTextValue(draftContent.getModule()));
        draftContent.setLanguage(normalizeTextValue(draftContent.getLanguage()));
        draftContent.setFramework(normalizeTextValue(draftContent.getFramework()));
        if (StringUtils.isBlank(draftContent.getProject()) && StringUtils.isNotBlank(draftContent.getRepository())) {
            draftContent.setProject(extractProjectFromRepository(draftContent.getRepository()));
        }
    }

    /**
     * 若 project 与工作空间编码相同，视为误填并清空，便于后续从 repository 推断
     */
    public void clearProjectIfSameAsWorkspace(KnowledgeDraftContent draftContent, String workspaceCode) {
        if (draftContent == null || StringUtils.isBlank(workspaceCode)) {
            return;
        }
        if (StringUtils.equals(draftContent.getProject(), workspaceCode)) {
            draftContent.setProject(null);
        }
    }

    /**
     * 裁剪空白，空串转 null
     */
    public String normalizeTextValue(String rawValue) {
        return StringUtils.trimToNull(rawValue);
    }

    /**
     * 仓库名规范化：去除首尾空白，org/repo 形式取最后一段
     */
    public String normalizeRepositoryName(String rawRepository) {
        String repository = StringUtils.trimToNull(rawRepository);
        if (repository == null) {
            return null;
        }
        if (repository.contains("/")) {
            repository = repository.substring(repository.lastIndexOf('/') + 1).trim();
        } else if (repository.contains("\\")) {
            repository = repository.substring(repository.lastIndexOf('\\') + 1).trim();
        }
        return StringUtils.trimToNull(repository);
    }

    private RecallContext copyContext(RecallContext source) {
        RecallContext target = new RecallContext();
        target.setTask(source.getTask());
        target.setCurrentPrompt(source.getCurrentPrompt());
        target.setWorkspace(source.getWorkspace());
        target.setWorkspaceId(source.getWorkspaceId());
        target.setRepository(source.getRepository());
        target.setProject(source.getProject());
        target.setModule(source.getModule());
        target.setBranch(source.getBranch());
        target.setLanguage(source.getLanguage());
        target.setFramework(source.getFramework());
        target.setCurrentFile(source.getCurrentFile());
        target.setModifiedFiles(source.getModifiedFiles());
        target.setDependencies(source.getDependencies());
        target.setIssue(source.getIssue());
        target.setRole(source.getRole());
        target.setTags(source.getTags());
        target.setFactTypes(source.getFactTypes());
        target.setKnowledgeTypes(source.getKnowledgeTypes());
        target.setLimit(source.getLimit());
        target.setExpandGraph(source.getExpandGraph());
        target.setGraphDepth(source.getGraphDepth());
        target.setGraphRelationTypes(source.getGraphRelationTypes());
        return target;
    }

    /**
     * 从仓库名推断项目名，例如 org/payment-service → payment
     */
    private String extractProjectFromRepository(String repository) {
        String repoName = repository;
        if (repository.contains("/")) {
            repoName = repository.substring(repository.lastIndexOf('/') + 1);
        }
        if (repoName.contains("-")) {
            return repoName.split("-")[0];
        }
        return repoName;
    }

    /**
     * 从文件路径推断模块名，取 src 下第二级目录
     */
    private String extractModuleFromFile(String currentFile) {
        String normalized = currentFile.replace('\\', '/');
        String[] segments = normalized.split("/");
        for (int index = 0; index < segments.length; index++) {
            if ("src".equals(segments[index]) && index + 2 < segments.length) {
                return segments[index + 2];
            }
        }
        if (segments.length >= 2) {
            return segments[segments.length - 2];
        }
        return null;
    }
}
