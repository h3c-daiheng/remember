package com.zhiyi.memory.engine;

import cn.hutool.json.JSONUtil;
import com.zhiyi.memory.context.ContextNormalizer;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.domain.SystemEventRequest;
import com.zhiyi.memory.util.MemoryJsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Summarize 引擎：将 Agent Event 载荷转为 Capture 草稿 Fact Blocks
 */
@Component
public class SummarizeEngine {

    private final ContextNormalizer contextNormalizer;

    /** 允许写入草稿的 Fact Block 类型 */
    private static final Set<String> ALLOWED_FACT_TYPES = new HashSet<String>();

    /** 从 action 等文本中提取文件路径，用于补全空 Artifact */
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("(?:[\\w.-]+/)+[\\w.-]+\\.\\w+");

    public SummarizeEngine(ContextNormalizer contextNormalizer) {
        this.contextNormalizer = contextNormalizer;
    }

    static {
        ALLOWED_FACT_TYPES.add("observation");
        ALLOWED_FACT_TYPES.add("decision");
        ALLOWED_FACT_TYPES.add("constraint");
        ALLOWED_FACT_TYPES.add("rule");
        ALLOWED_FACT_TYPES.add("evidence");
        ALLOWED_FACT_TYPES.add("action");
        ALLOWED_FACT_TYPES.add("outcome");
    }

    /**
     * 将统一 Event 转为 Capture 草稿内容
     */
    public KnowledgeDraftContent toFactBlocks(SystemEventRequest eventRequest) {
        KnowledgeDraftContent draftContent = new KnowledgeDraftContent();
        Map<String, Object> payload = eventRequest.getPayload();
        String task = MemoryJsonUtil.readString(payload, "task");
        String title = MemoryJsonUtil.readString(payload, "title");

        draftContent.setTitle(buildTitle(title, task, eventRequest.getModule()));
        draftContent.setProject(resolveProjectFromEvent(eventRequest));
        draftContent.setModule(eventRequest.getModule());
        draftContent.setRepository(eventRequest.getRepository());
        draftContent.setTags(buildTags(eventRequest, payload));
        draftContent.setFacts(buildFacts(payload));
        draftContent.setArtifacts(buildArtifacts(eventRequest, payload));
        contextNormalizer.clearProjectIfSameAsWorkspace(draftContent, eventRequest.getWorkspace());
        contextNormalizer.normalizeDraftContent(draftContent);
        return draftContent;
    }

    /**
     * 解析业务项目名：优先 Event.project，禁止回退为工作空间编码 workspace
     */
    private String resolveProjectFromEvent(SystemEventRequest eventRequest) {
        String project = StringUtils.trimToNull(eventRequest.getProject());
        if (StringUtils.isNotBlank(project) && StringUtils.equals(project, eventRequest.getWorkspace())) {
            return null;
        }
        return project;
    }

    /**
     * 构建 Artifact 列表：剔除空对象，必要时从 action / modifiedFiles 推断关联文件
     */
    private List<ArtifactDto> buildArtifacts(SystemEventRequest eventRequest, Map<String, Object> payload) {
        List<ArtifactDto> artifacts = sanitizeArtifacts(eventRequest.getArtifacts());
        if (!artifacts.isEmpty()) {
            return artifacts;
        }
        return inferArtifactsFromPayload(payload, eventRequest.getMetadata());
    }

    /**
     * 过滤 Agent 误传的空 Artifact（{}），避免 Review 页出现 [] · 占位
     */
    private List<ArtifactDto> sanitizeArtifacts(List<ArtifactDto> rawArtifacts) {
        List<ArtifactDto> artifacts = new ArrayList<ArtifactDto>();
        if (rawArtifacts == null) {
            return artifacts;
        }
        Set<String> seenReferences = new HashSet<String>();
        for (ArtifactDto artifactDto : rawArtifacts) {
            if (artifactDto == null) {
                continue;
            }
            String contentRef = StringUtils.trimToEmpty(artifactDto.getContentRef());
            String artifactUrl = StringUtils.trimToEmpty(artifactDto.getArtifactUrl());
            String artifactType = StringUtils.trimToEmpty(artifactDto.getArtifactType());
            if (StringUtils.isAllBlank(contentRef, artifactUrl, artifactType)) {
                continue;
            }
            String dedupeKey = StringUtils.defaultString(artifactDto.getArtifactRole()) + ":" + StringUtils.defaultIfBlank(contentRef, artifactUrl);
            if (seenReferences.contains(dedupeKey)) {
                continue;
            }
            seenReferences.add(dedupeKey);
            if (StringUtils.isBlank(artifactDto.getArtifactRole())) {
                artifactDto.setArtifactRole("origin");
            }
            if (StringUtils.isBlank(artifactType)) {
                artifactDto.setArtifactType("code");
            }
            if (StringUtils.isBlank(contentRef) && StringUtils.isNotBlank(artifactUrl)) {
                artifactDto.setContentRef(artifactUrl);
            }
            artifacts.add(artifactDto);
        }
        return artifacts;
    }

    /**
     * 当未传有效 Artifact 时，从 action 文本与 metadata.modifiedFiles 推断关联文件
     */
    @SuppressWarnings("unchecked")
    private List<ArtifactDto> inferArtifactsFromPayload(Map<String, Object> payload, Map<String, Object> metadata) {
        LinkedHashSet<String> filePaths = new LinkedHashSet<String>();
        String action = MemoryJsonUtil.readString(payload, "action");
        if (StringUtils.isNotBlank(action)) {
            Matcher matcher = FILE_PATH_PATTERN.matcher(action);
            while (matcher.find()) {
                filePaths.add(matcher.group());
            }
        }
        if (metadata != null && metadata.get("modifiedFiles") instanceof List) {
            List<Object> modifiedFiles = (List<Object>) metadata.get("modifiedFiles");
            for (Object filePath : modifiedFiles) {
                if (filePath != null && StringUtils.isNotBlank(String.valueOf(filePath))) {
                    filePaths.add(String.valueOf(filePath).trim());
                }
            }
        }
        List<ArtifactDto> artifacts = new ArrayList<ArtifactDto>();
        for (String filePath : filePaths) {
            ArtifactDto artifactDto = new ArtifactDto();
            artifactDto.setArtifactType("code");
            artifactDto.setArtifactRole("origin");
            artifactDto.setContentRef(filePath);
            artifacts.add(artifactDto);
        }
        return artifacts;
    }

    /**
     * 根据标题、任务描述或模块名生成草稿标题
     */
    private String buildTitle(String title, String task, String module) {
        if (StringUtils.isNotBlank(title)) {
            if (title.length() > 80) {
                return title.substring(0, 80);
            }
            return title;
        }
        if (StringUtils.isNotBlank(task)) {
            if (task.length() > 80) {
                return task.substring(0, 80);
            }
            return task;
        }
        if (StringUtils.isNotBlank(module)) {
            return module + " 经验沉淀";
        }
        return "Agent 任务经验";
    }

    private List<String> buildTags(SystemEventRequest eventRequest, Map<String, Object> payload) {
        List<String> tags = new ArrayList<String>();
        if (StringUtils.isNotBlank(eventRequest.getModule())) {
            tags.add(eventRequest.getModule());
        }
        tags.addAll(MemoryJsonUtil.readStringList(payload, "tags"));
        return MemoryJsonUtil.sanitizeTags(tags);
    }

    /**
     * 构建 Fact Block 列表：优先使用 payload.facts；否则按分字段映射，并去重
     */
    private List<FactBlock> buildFacts(Map<String, Object> payload) {
        List<FactBlock> directFacts = parseFactsFromPayload(payload);
        if (!directFacts.isEmpty()) {
            return directFacts;
        }

        String observation = MemoryJsonUtil.readString(payload, "observation");
        String decision = MemoryJsonUtil.readString(payload, "decision");
        String action = MemoryJsonUtil.readString(payload, "action");
        String constraint = MemoryJsonUtil.readString(payload, "constraint");
        String outcome = MemoryJsonUtil.readString(payload, "outcome");
        String evidence = MemoryJsonUtil.readString(payload, "evidence");
        String summary = MemoryJsonUtil.readString(payload, "summary");
        String task = MemoryJsonUtil.readString(payload, "task");

        // 兼容旧版 Agent：仅传 task + summary 时，用 summary 作为 observation，不再重复写入 decision/action
        if (StringUtils.isBlank(observation) && StringUtils.isNotBlank(summary) && !StringUtils.equals(summary, task)) {
            observation = summary;
        }

        List<FactBlock> facts = new ArrayList<FactBlock>();
        int sortOrder = 0;
        sortOrder = appendFactIfUnique(facts, "observation", observation, sortOrder);
        sortOrder = appendFactIfUnique(facts, "decision", decision, sortOrder);
        sortOrder = appendFactIfUnique(facts, "constraint", constraint, sortOrder);
        sortOrder = appendFactIfUnique(facts, "evidence", evidence, sortOrder);
        sortOrder = appendFactIfUnique(facts, "action", action, sortOrder);
        sortOrder = appendFactIfUnique(facts, "outcome", outcome, sortOrder);

        if (facts.isEmpty()) {
            facts.add(buildFact("observation", "Agent 完成任务，待补充详细经验", 0));
        }
        return facts;
    }

    /**
     * 解析 payload.facts 数组，Agent 可直接传入结构化 Fact Blocks
     */
    @SuppressWarnings("unchecked")
    private List<FactBlock> parseFactsFromPayload(Map<String, Object> payload) {
        List<FactBlock> facts = new ArrayList<FactBlock>();
        if (payload == null || !payload.containsKey("facts")) {
            return facts;
        }
        Object factsValue = payload.get("facts");
        if (!(factsValue instanceof List)) {
            return facts;
        }
        List<Object> rawList = (List<Object>) factsValue;
        Set<String> seenTexts = new HashSet<String>();
        int sortOrder = 0;
        for (Object item : rawList) {
            FactBlock factBlock = null;
            if (item instanceof Map) {
                factBlock = JSONUtil.toBean(JSONUtil.toJsonStr(item), FactBlock.class);
            } else if (item instanceof FactBlock) {
                factBlock = (FactBlock) item;
            }
            if (factBlock == null || StringUtils.isBlank(factBlock.getType()) || StringUtils.isBlank(factBlock.getText())) {
                continue;
            }
            if (!ALLOWED_FACT_TYPES.contains(factBlock.getType())) {
                continue;
            }
            String normalizedText = factBlock.getText().trim();
            if (seenTexts.contains(normalizedText)) {
                continue;
            }
            seenTexts.add(normalizedText);
            factBlock.setText(normalizedText);
            factBlock.setSortOrder(sortOrder++);
            facts.add(factBlock);
        }
        return facts;
    }

    /**
     * 追加 Fact Block，跳过空文本及与已有块完全重复的内容
     */
    private int appendFactIfUnique(List<FactBlock> facts, String type, String text, int sortOrder) {
        if (StringUtils.isBlank(text)) {
            return sortOrder;
        }
        String normalizedText = text.trim();
        for (FactBlock existing : facts) {
            if (StringUtils.equals(existing.getText(), normalizedText)) {
                return sortOrder;
            }
        }
        facts.add(buildFact(type, normalizedText, sortOrder));
        return sortOrder + 1;
    }

    private FactBlock buildFact(String type, String text, int sortOrder) {
        FactBlock factBlock = new FactBlock();
        factBlock.setType(type);
        factBlock.setText(text);
        factBlock.setSortOrder(sortOrder);
        return factBlock;
    }
}
