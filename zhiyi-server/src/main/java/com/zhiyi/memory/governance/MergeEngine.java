package com.zhiyi.memory.governance;

import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.domain.vo.KnowledgeSupersedeRequest;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.ArtifactDto;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.GovernanceMergeConfirmRequest;
import com.zhiyi.memory.domain.GovernanceMergeLlmResult;
import com.zhiyi.memory.domain.GovernanceMergePreviewView;
import com.zhiyi.memory.domain.KnowledgeAggregate;
import com.zhiyi.memory.domain.KnowledgeSaveRequest;
import com.zhiyi.memory.graph.GraphGovernanceService;
import com.zhiyi.memory.knowledge.KnowledgeService;
import com.zhiyi.memory.prompt.MergePromptTemplates;
import com.zhiyi.memory.timeline.KnowledgeTimelineService;
import com.zhiyi.modelgateway.ModelGateway;
import com.zhiyi.modelgateway.config.ModelGatewayProperties;
import com.zhiyi.modelgateway.domain.ChatCompletionRequest;
import com.zhiyi.modelgateway.domain.ChatCompletionResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 治理合并引擎：规则/LLM 预览 + 发布新版并替代源记忆
 */
@Component
public class MergeEngine {

    private static final Logger log = LoggerFactory.getLogger(MergeEngine.class);

    private static final Set<String> ALLOWED_FACT_TYPES = new HashSet<String>();

    static {
        ALLOWED_FACT_TYPES.add("observation");
        ALLOWED_FACT_TYPES.add("decision");
        ALLOWED_FACT_TYPES.add("constraint");
        ALLOWED_FACT_TYPES.add("rule");
        ALLOWED_FACT_TYPES.add("evidence");
        ALLOWED_FACT_TYPES.add("action");
        ALLOWED_FACT_TYPES.add("outcome");
    }

    private final KnowledgeService knowledgeService;
    private final GraphGovernanceService graphGovernanceService;
    private final KnowledgeTimelineService knowledgeTimelineService;
    private final IndexReconciler indexReconciler;
    private final ModelGateway modelGateway;
    private final ModelGatewayProperties modelGatewayProperties;

    public MergeEngine(KnowledgeService knowledgeService,
                       GraphGovernanceService graphGovernanceService,
                       KnowledgeTimelineService knowledgeTimelineService,
                       IndexReconciler indexReconciler,
                       ModelGateway modelGateway,
                       ModelGatewayProperties modelGatewayProperties) {
        this.knowledgeService = knowledgeService;
        this.graphGovernanceService = graphGovernanceService;
        this.knowledgeTimelineService = knowledgeTimelineService;
        this.indexReconciler = indexReconciler;
        this.modelGateway = modelGateway;
        this.modelGatewayProperties = modelGatewayProperties;
    }

    /**
     * 生成合并预览：先规则合并，Experience 类型可选 LLM 润色
     */
    public GovernanceMergePreviewView buildPreview(List<Long> sourceKnowledgeIds,
                                                   String workspaceId,
                                                   boolean useLlm) {
        List<KnowledgeAggregate> sourceList = loadPublishedSources(sourceKnowledgeIds, workspaceId);
        if (sourceList.size() < 2) {
            throw new BusinessException(400, "合并至少需要 2 条已发布记忆");
        }
        validateSameKnowledgeType(sourceList);

        GovernanceMergePreviewView previewView = buildRuleBasedPreview(sourceList);
        if (useLlm && MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE.equals(previewView.getKnowledgeType())) {
            try {
                GovernanceMergePreviewView llmPreview = enhancePreviewWithLlm(sourceList, previewView, workspaceId);
                if (llmPreview != null) {
                    return llmPreview;
                }
            } catch (Exception exception) {
                log.warn("LLM 合并预览失败，回退规则合并 workspaceId={} message={}",
                        workspaceId, exception.getMessage());
            }
        }
        previewView.setPreviewSource("rule");
        return previewView;
    }

    /**
     * 确认合并：创建新版已发布记忆，supersede 全部源记忆
     */
    @Transactional(rollbackFor = Exception.class)
    public Long confirmMerge(GovernanceMergeConfirmRequest confirmRequest,
                             String workspaceId,
                             Long operatorId,
                             String memberRole) {
        if (confirmRequest == null) {
            throw new BusinessException(400, "合并确认请求不能为空");
        }
        if (StringUtils.isBlank(confirmRequest.getTitle())) {
            throw new BusinessException(400, "合并标题不能为空");
        }
        if (confirmRequest.getFacts() == null || confirmRequest.getFacts().isEmpty()) {
            throw new BusinessException(400, "合并后至少保留一条 Fact");
        }

        List<Long> sourceIdList = dedupeSourceKnowledgeIds(confirmRequest.getSourceKnowledgeIds());
        confirmRequest.setSourceKnowledgeIds(sourceIdList);
        List<KnowledgeAggregate> sourceList = loadPublishedSources(sourceIdList, workspaceId);
        validateSameKnowledgeType(sourceList);

        KnowledgeSaveRequest saveRequest = new KnowledgeSaveRequest();
        saveRequest.setTitle(StringUtils.left(confirmRequest.getTitle().trim(), 256));
        saveRequest.setProject(confirmRequest.getProject());
        saveRequest.setModule(confirmRequest.getModule());
        saveRequest.setRepository(confirmRequest.getRepository());
        saveRequest.setTags(confirmRequest.getTags());
        saveRequest.setFacts(sanitizeFacts(confirmRequest.getFacts()));
        saveRequest.setArtifacts(confirmRequest.getArtifacts());
        saveRequest.setKnowledgeType(StringUtils.defaultIfBlank(
                confirmRequest.getKnowledgeType(), MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE));
        saveRequest.setPublish(true);

        Long newKnowledgeId = knowledgeService.create(saveRequest, operatorId, workspaceId);
        indexReconciler.inheritRecallCount(newKnowledgeId, sourceIdList);
        indexReconciler.reconcilePublishedKnowledge(newKnowledgeId, workspaceId);

        String comment = StringUtils.defaultString(confirmRequest.getComment(), "治理：合并优化发布新版");
        for (Long sourceId : sourceIdList) {
            KnowledgeSupersedeRequest supersedeRequest = new KnowledgeSupersedeRequest();
            supersedeRequest.setPredecessorId(sourceId);
            supersedeRequest.setComment(comment);
            graphGovernanceService.supersede(newKnowledgeId, supersedeRequest, workspaceId, operatorId, memberRole);
            knowledgeTimelineService.recordMergeOptimize(
                    newKnowledgeId, sourceId, workspaceId, operatorId, comment);
        }
        return newKnowledgeId;
    }

    /**
     * 规则合并：Fact/Artifact/标签去重，标题取最长
     */
    private GovernanceMergePreviewView buildRuleBasedPreview(List<KnowledgeAggregate> sourceList) {
        GovernanceMergePreviewView previewView = new GovernanceMergePreviewView();
        List<Long> sourceIds = new ArrayList<Long>();
        LinkedHashSet<String> tagSet = new LinkedHashSet<String>();
        LinkedHashSet<String> factTextSet = new LinkedHashSet<String>();
        List<FactBlock> mergedFacts = new ArrayList<FactBlock>();
        List<ArtifactDto> mergedArtifacts = new ArrayList<ArtifactDto>();
        Set<String> artifactKeySet = new HashSet<String>();

        String bestTitle = "";
        KnowledgeAggregate primaryAggregate = sourceList.get(0);
        for (KnowledgeAggregate aggregate : sourceList) {
            sourceIds.add(aggregate.getId());
            if (StringUtils.isNotBlank(aggregate.getTitle()) && aggregate.getTitle().length() > bestTitle.length()) {
                bestTitle = aggregate.getTitle();
                primaryAggregate = aggregate;
            }
            if (aggregate.getTags() != null) {
                tagSet.addAll(aggregate.getTags());
            }
            if (aggregate.getFacts() != null) {
                for (FactBlock factBlock : aggregate.getFacts()) {
                    if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                        continue;
                    }
                    String normalizedText = factBlock.getText().trim();
                    if (factTextSet.contains(normalizedText)) {
                        continue;
                    }
                    factTextSet.add(normalizedText);
                    FactBlock copyFact = new FactBlock();
                    copyFact.setType(factBlock.getType());
                    copyFact.setText(normalizedText);
                    copyFact.setSortOrder(mergedFacts.size());
                    mergedFacts.add(copyFact);
                }
            }
            if (aggregate.getArtifacts() != null) {
                for (ArtifactDto artifactDto : aggregate.getArtifacts()) {
                    if (artifactDto == null) {
                        continue;
                    }
                    String artifactKey = StringUtils.defaultString(artifactDto.getArtifactRole()) + ":"
                            + StringUtils.defaultIfBlank(artifactDto.getContentRef(), artifactDto.getArtifactUrl());
                    if (artifactKeySet.contains(artifactKey)) {
                        continue;
                    }
                    artifactKeySet.add(artifactKey);
                    mergedArtifacts.add(artifactDto);
                }
            }
        }

        previewView.setSourceKnowledgeIds(sourceIds);
        previewView.setTitle(StringUtils.isNotBlank(bestTitle) ? bestTitle : "合并优化经验");
        previewView.setFacts(mergedFacts);
        previewView.setTags(new ArrayList<String>(tagSet));
        previewView.setArtifacts(mergedArtifacts);
        previewView.setProject(primaryAggregate.getProject());
        previewView.setModule(primaryAggregate.getModule());
        previewView.setRepository(primaryAggregate.getRepository());
        previewView.setKnowledgeType(primaryAggregate.getKnowledgeType());
        return previewView;
    }

    /**
     * LLM 增强合并预览
     */
    private GovernanceMergePreviewView enhancePreviewWithLlm(List<KnowledgeAggregate> sourceList,
                                                             GovernanceMergePreviewView rulePreview,
                                                             String workspaceId) {
        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .profile(modelGatewayProperties.getMergeProfile())
                .systemPrompt(MergePromptTemplates.buildSystemPrompt())
                .userPrompt(MergePromptTemplates.buildUserPrompt(sourceList))
                .temperature(0.2)
                .maxTokens(4096)
                .responseFormat("json_object")
                .tenantId(workspaceId)
                .build();

        ChatCompletionResponse chatResponse = modelGateway.chat(chatRequest);
        GovernanceMergeLlmResult llmResult = parseLlmResult(chatResponse.getContent());
        if (llmResult == null || StringUtils.isBlank(llmResult.getTitle())
                || llmResult.getFacts() == null || llmResult.getFacts().isEmpty()) {
            return null;
        }

        GovernanceMergePreviewView previewView = new GovernanceMergePreviewView();
        previewView.setSourceKnowledgeIds(rulePreview.getSourceKnowledgeIds());
        previewView.setTitle(StringUtils.left(llmResult.getTitle().trim(), 256));
        previewView.setFacts(sanitizeFacts(llmResult.getFacts()));
        previewView.setTags(rulePreview.getTags());
        previewView.setArtifacts(rulePreview.getArtifacts());
        previewView.setProject(rulePreview.getProject());
        previewView.setModule(rulePreview.getModule());
        previewView.setRepository(rulePreview.getRepository());
        previewView.setKnowledgeType(rulePreview.getKnowledgeType());
        previewView.setPreviewSource("llm");
        previewView.setMergeSummary(llmResult.getMergeSummary());
        return previewView;
    }

    private GovernanceMergeLlmResult parseLlmResult(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        try {
            return JSONUtil.toBean(content, GovernanceMergeLlmResult.class);
        } catch (Exception exception) {
            log.debug("解析 LLM 合并结果失败 content={}", StringUtils.left(content, 200));
            return null;
        }
    }

    private List<FactBlock> sanitizeFacts(List<FactBlock> rawFacts) {
        List<FactBlock> factList = new ArrayList<FactBlock>();
        if (rawFacts == null) {
            return factList;
        }
        Set<String> seenTexts = new HashSet<String>();
        int sortOrder = 0;
        for (FactBlock factBlock : rawFacts) {
            if (factBlock == null || StringUtils.isBlank(factBlock.getText())) {
                continue;
            }
            String type = StringUtils.defaultIfBlank(factBlock.getType(), "observation");
            if (!ALLOWED_FACT_TYPES.contains(type)) {
                continue;
            }
            String normalizedText = factBlock.getText().trim();
            if (seenTexts.contains(normalizedText)) {
                continue;
            }
            seenTexts.add(normalizedText);
            FactBlock sanitized = new FactBlock();
            sanitized.setType(type);
            sanitized.setText(normalizedText);
            sanitized.setSortOrder(sortOrder++);
            factList.add(sanitized);
        }
        return factList;
    }

    private List<KnowledgeAggregate> loadPublishedSources(List<Long> sourceKnowledgeIds, String workspaceId) {
        List<Long> normalizedIdList = dedupeSourceKnowledgeIds(sourceKnowledgeIds);
        if (normalizedIdList.size() < 2) {
            throw new BusinessException(400, "合并至少需要 2 条源记忆");
        }
        List<KnowledgeAggregate> sourceList = new ArrayList<KnowledgeAggregate>();
        for (Long knowledgeId : normalizedIdList) {
            KnowledgeAggregate aggregate = knowledgeService.getDetail(knowledgeId, workspaceId);
            if (aggregate.getLifecycleStatus() == null
                    || aggregate.getLifecycleStatus() != MemoryConstants.LIFECYCLE_PUBLISHED) {
                throw new BusinessException(400, "仅已发布记忆可参与合并：" + knowledgeId);
            }
            sourceList.add(aggregate);
        }
        return sourceList;
    }

    /**
     * 源记忆 ID 去重并保持首次出现顺序
     */
    private List<Long> dedupeSourceKnowledgeIds(List<Long> sourceKnowledgeIds) {
        if (sourceKnowledgeIds == null || sourceKnowledgeIds.isEmpty()) {
            return new ArrayList<Long>();
        }
        LinkedHashSet<Long> uniqueIdSet = new LinkedHashSet<Long>();
        for (Long knowledgeId : sourceKnowledgeIds) {
            if (knowledgeId != null) {
                uniqueIdSet.add(knowledgeId);
            }
        }
        return new ArrayList<Long>(uniqueIdSet);
    }

    private void validateSameKnowledgeType(List<KnowledgeAggregate> sourceList) {
        String knowledgeType = null;
        for (KnowledgeAggregate aggregate : sourceList) {
            if (knowledgeType == null) {
                knowledgeType = aggregate.getKnowledgeType();
            } else if (!StringUtils.equals(knowledgeType, aggregate.getKnowledgeType())) {
                throw new BusinessException(400, "合并源记忆须为同一知识类型");
            }
        }
    }
}
