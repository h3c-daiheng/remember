package com.zhiyi.memory.engine;

import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.MemoryConstants;
import com.zhiyi.memory.domain.CaptureDraftView;
import com.zhiyi.memory.domain.CaptureRouteChecklistHint;
import com.zhiyi.memory.domain.CaptureRouteLlmResult;
import com.zhiyi.memory.domain.CaptureRouteSuggestion;
import com.zhiyi.memory.domain.CaptureSimilarKnowledgeHint;
import com.zhiyi.memory.domain.FactBlock;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import com.zhiyi.memory.prompt.RoutePromptTemplates;
import com.zhiyi.modelgateway.ModelGateway;
import com.zhiyi.modelgateway.config.ModelGatewayProperties;
import com.zhiyi.modelgateway.domain.ChatCompletionRequest;
import com.zhiyi.modelgateway.domain.ChatCompletionResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capture 路由引擎：规则引擎快速判定 + 低置信度时 LLM 增强，只读分析不写库
 */
@Component
public class CaptureRouteEngine {

    private static final Logger log = LoggerFactory.getLogger(CaptureRouteEngine.class);

    /** 标题中暗示规范类内容的词 */
    private static final String[] RULE_TITLE_KEYWORDS = {"规范", "必须", "禁止", "应该", "不得", "规则", "约束"};

    /** 标题中暗示流程类内容的词 */
    private static final String[] WORKFLOW_TITLE_KEYWORDS = {"步骤", "流程", "操作", "如何", "怎么"};

    /** 标题中暗示架构决策类内容的词 */
    private static final String[] DECISION_TITLE_KEYWORDS = {"ADR", "架构", "决策", "为什么", "选型", "方案取舍"};

    private final ModelGateway modelGateway;
    private final ModelGatewayProperties modelGatewayProperties;
    private final SimilarKnowledgeFinder similarKnowledgeFinder;
    private final CaptureReviewAssistEngine captureReviewAssistEngine;

    public CaptureRouteEngine(ModelGateway modelGateway,
                              ModelGatewayProperties modelGatewayProperties,
                              SimilarKnowledgeFinder similarKnowledgeFinder,
                              CaptureReviewAssistEngine captureReviewAssistEngine) {
        this.modelGateway = modelGateway;
        this.modelGatewayProperties = modelGatewayProperties;
        this.similarKnowledgeFinder = similarKnowledgeFinder;
        this.captureReviewAssistEngine = captureReviewAssistEngine;
    }

    /**
     * 分析 Capture 草稿，返回归类建议（无工作空间时不触发 LLM）
     */
    public CaptureRouteSuggestion suggest(CaptureDraftView draftView) {
        return suggest(draftView, null, false);
    }

    /**
     * 分析 Capture 草稿：规则引擎优先，低置信度或规则冲突时调 LLM 增强
     */
    public CaptureRouteSuggestion suggest(CaptureDraftView draftView, String workspaceId) {
        return suggest(draftView, workspaceId, false);
    }

    /**
     * 分析 Capture 草稿；forceLlm=true 时忽略置信度阈值，由 Review 人主动触发 AI 增强
     */
    public CaptureRouteSuggestion suggest(CaptureDraftView draftView, String workspaceId, boolean forceLlm) {
        CaptureRouteSuggestion ruleSuggestion = suggestByRule(draftView);
        ruleSuggestion.setSuggestionSource("rule");

        KnowledgeDraftContent draftContent = draftView.getDraftContent();
        List<CaptureSimilarKnowledgeHint> similarKnowledgeList = new ArrayList<CaptureSimilarKnowledgeHint>();
        if (StringUtils.isNotBlank(workspaceId)) {
            similarKnowledgeList = similarKnowledgeFinder.findSimilar(draftContent, workspaceId);
        }
        ruleSuggestion.setSimilarKnowledge(similarKnowledgeList);

        // 规则预审：全量检查清单 AI 预填（不依赖 LLM）
        List<CaptureRouteChecklistHint> ruleChecklistHints = captureReviewAssistEngine.analyze(
                draftContent, similarKnowledgeList);
        ruleSuggestion.setChecklistHints(ruleChecklistHints);

        if (!forceLlm && !shouldEnhanceWithLlm(ruleSuggestion, draftContent)) {
            return ruleSuggestion;
        }
        if (!modelGatewayProperties.isRouteLlmEnabled()) {
            if (forceLlm) {
                List<String> reasons = new ArrayList<String>(ruleSuggestion.getReasons());
                reasons.add("AI 增强未启用，请联系管理员开启 route-llm-enabled");
                ruleSuggestion.setReasons(reasons);
            }
            return ruleSuggestion;
        }
        if (StringUtils.isBlank(workspaceId) || !modelGatewayProperties.isRemoteMode()) {
            if (forceLlm) {
                List<String> reasons = new ArrayList<String>(ruleSuggestion.getReasons());
                reasons.add("AI 增强不可用：请确认 model-gateway 已配置为 remote 模式");
                ruleSuggestion.setReasons(reasons);
            }
            return ruleSuggestion;
        }

        try {
            CaptureRouteLlmResult llmResult = invokeLlm(draftContent, ruleSuggestion,
                    similarKnowledgeList, workspaceId);
            CaptureRouteSuggestion merged = mergeSuggestions(ruleSuggestion, llmResult, similarKnowledgeList);
            merged.setChecklistHints(mergeChecklistHints(ruleChecklistHints, llmResult.getChecklistHints()));
            return merged;
        } catch (Exception exception) {
            log.warn("Capture 归类 LLM 增强失败，回退规则引擎 workspaceId={} message={}",
                    workspaceId, exception.getMessage());
            List<String> reasons = new ArrayList<String>(ruleSuggestion.getReasons());
            reasons.add("AI 增强暂不可用，已使用规则引擎结果");
            ruleSuggestion.setReasons(reasons);
            return ruleSuggestion;
        }
    }

    /**
     * 纯规则引擎分析，与 V1 逻辑一致
     */
    public CaptureRouteSuggestion suggestByRule(CaptureDraftView draftView) {
        CaptureRouteSuggestion suggestion = new CaptureRouteSuggestion();
        List<String> reasons = new ArrayList<String>();
        KnowledgeDraftContent content = draftView.getDraftContent();
        if (content == null || content.getFacts() == null || content.getFacts().isEmpty()) {
            suggestion.setRecommendedAction("reject");
            suggestion.setConfidence(0.9D);
            suggestion.setRecommendedRejectReason("REJECT_LOW_QUALITY");
            reasons.add("草稿无 Fact Block 内容");
            suggestion.setReasons(reasons);
            suggestion.setAlternatives(buildAlternatives("reject"));
            return suggestion;
        }

        if (StringUtils.isNotBlank(content.getSubmittedKnowledgeType())) {
            String submittedType = content.getSubmittedKnowledgeType().trim();
            suggestion.setRecommendedAction(resolveApproveActionForType(submittedType));
            suggestion.setTargetKnowledgeType(submittedType);
            suggestion.setConfidence(0.9D);
            reasons.add("Agent 提交时声明为 " + submittedType + "，默认按此类型确认");
            suggestion.setReasons(reasons);
            suggestion.setAlternatives(buildAlternatives(suggestion.getRecommendedAction()));
            return suggestion;
        }

        Map<String, Integer> typeCount = countFactTypes(content.getFacts());
        int ruleCount = getCount(typeCount, "rule");
        int constraintCount = getCount(typeCount, "constraint");
        int actionCount = getCount(typeCount, "action");
        int decisionCount = getCount(typeCount, "decision");
        int outcomeCount = getCount(typeCount, "outcome");
        int observationCount = getCount(typeCount, "observation");
        String title = StringUtils.defaultString(content.getTitle());

        if (ruleCount + constraintCount >= 2 && outcomeCount == 0 && decisionCount == 0) {
            suggestion.setRecommendedAction(MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE);
            suggestion.setTargetKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_RULE);
            suggestion.setRecommendedRejectReason("REJECT_DOC_GAP");
            suggestion.setConfidence(0.85D);
            reasons.add("含 " + (ruleCount + constraintCount) + " 条 rule/constraint Fact，无 outcome");
            if (containsAnyKeyword(title, RULE_TITLE_KEYWORDS)) {
                reasons.add("标题含规范/必须类表述");
                suggestion.setConfidence(0.92D);
            }
        } else if (actionCount >= 2 && decisionCount == 0 && outcomeCount == 0 && ruleCount == 0) {
            suggestion.setRecommendedAction(MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW);
            suggestion.setTargetKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW);
            suggestion.setRecommendedRejectReason("REJECT_NOT_EXPERIENCE");
            suggestion.setConfidence(0.78D);
            reasons.add("以 action 步骤为主，缺少 decision/outcome");
            if (containsAnyKeyword(title, WORKFLOW_TITLE_KEYWORDS)) {
                reasons.add("标题含步骤/流程类表述");
                suggestion.setConfidence(0.86D);
            }
        } else if (decisionCount >= 1 && outcomeCount == 0 && observationCount == 0 && actionCount <= 1) {
            suggestion.setRecommendedAction(MemoryConstants.REVIEW_ACTION_ROUTE_TO_DECISION);
            suggestion.setTargetKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_DECISION);
            suggestion.setRecommendedRejectReason("REJECT_NOT_EXPERIENCE");
            suggestion.setConfidence(0.82D);
            reasons.add("含 decision Fact，无 outcome/observation 验证，更宜沉淀为架构决策");
            if (containsAnyKeyword(title, DECISION_TITLE_KEYWORDS)) {
                reasons.add("标题含架构/决策类表述");
                suggestion.setConfidence(0.9D);
            }
        } else if (decisionCount >= 1 && (outcomeCount >= 1 || observationCount >= 1)) {
            suggestion.setRecommendedAction(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE);
            suggestion.setTargetKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
            suggestion.setConfidence(0.8D);
            reasons.add("含 decision 与验证信息，适合发布为经验");
        } else if (ruleCount >= 1 || constraintCount >= 1) {
            suggestion.setRecommendedAction(MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE);
            suggestion.setTargetKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_RULE);
            suggestion.setRecommendedRejectReason("REJECT_DOC_GAP");
            suggestion.setConfidence(0.72D);
            reasons.add("含 rule/constraint Fact，更宜写入规范层");
        } else {
            suggestion.setRecommendedAction(MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE);
            suggestion.setTargetKnowledgeType(MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE);
            suggestion.setConfidence(0.55D);
            reasons.add("未命中明确路由规则，默认建议发布为经验");
        }

        suggestion.setReasons(reasons);
        suggestion.setAlternatives(buildAlternatives(suggestion.getRecommendedAction()));
        return suggestion;
    }

    /**
     * 判断是否需要调用 LLM：置信度低于阈值，或规则与内容信号冲突
     */
    private boolean shouldEnhanceWithLlm(CaptureRouteSuggestion ruleSuggestion, KnowledgeDraftContent content) {
        if (!modelGatewayProperties.isRouteLlmEnabled()) {
            return false;
        }
        if (ruleSuggestion.getConfidence() < modelGatewayProperties.getRouteLlmConfidenceThreshold()) {
            return true;
        }
        return hasRuleContentConflict(ruleSuggestion, content);
    }

    /**
     * 规则推荐与 Fact 组成不一致时视为冲突，需 LLM 复核
     */
    private boolean hasRuleContentConflict(CaptureRouteSuggestion ruleSuggestion, KnowledgeDraftContent content) {
        if (content == null || content.getFacts() == null || content.getFacts().isEmpty()) {
            return false;
        }
        Map<String, Integer> typeCount = countFactTypes(content.getFacts());
        int ruleCount = getCount(typeCount, "rule") + getCount(typeCount, "constraint");
        int decisionCount = getCount(typeCount, "decision");
        int outcomeCount = getCount(typeCount, "outcome") + getCount(typeCount, "observation");
        String action = ruleSuggestion.getRecommendedAction();

        if (MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE.equals(action) && ruleCount >= 2 && outcomeCount == 0) {
            return true;
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE.equals(action) && decisionCount >= 1 && outcomeCount >= 1) {
            return true;
        }
        return MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW.equals(action)
                && decisionCount >= 1
                && outcomeCount >= 1;
    }

    /**
     * 调用 ai-gateway 获取 LLM 归类结果
     */
    private CaptureRouteLlmResult invokeLlm(KnowledgeDraftContent draftContent,
                                            CaptureRouteSuggestion ruleSuggestion,
                                            List<CaptureSimilarKnowledgeHint> similarKnowledgeList,
                                            String workspaceId) {
        String systemPrompt = RoutePromptTemplates.buildSystemPrompt();
        String userPrompt = RoutePromptTemplates.buildUserPrompt(draftContent, ruleSuggestion, similarKnowledgeList);

        ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                .profile(modelGatewayProperties.getRouteProfile())
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .temperature(0.2)
                .maxTokens(2048)
                .responseFormat("json_object")
                .tenantId(workspaceId)
                .build();

        ChatCompletionResponse chatResponse = modelGateway.chat(chatRequest);
        return parseLlmResult(chatResponse.getContent());
    }

    /**
     * 合并规则引擎与 LLM 结果，以 LLM 为主、保留规则对照信息
     */
    private CaptureRouteSuggestion mergeSuggestions(CaptureRouteSuggestion ruleSuggestion,
                                                    CaptureRouteLlmResult llmResult,
                                                    List<CaptureSimilarKnowledgeHint> similarKnowledgeList) {
        CaptureRouteSuggestion merged = mapLlmResult(llmResult);
        merged.setSimilarKnowledge(similarKnowledgeList);
        merged.setAlternatives(buildAlternatives(merged.getRecommendedAction()));

        boolean sameAction = StringUtils.equals(ruleSuggestion.getRecommendedAction(), merged.getRecommendedAction());
        merged.setSuggestionSource(sameAction ? "hybrid" : "llm");

        List<String> reasons = new ArrayList<String>();
        reasons.add("[规则] " + formatActionLabel(ruleSuggestion.getRecommendedAction())
                + "（置信度 " + Math.round(ruleSuggestion.getConfidence() * 100) + "%）");
        if (llmResult.getReasons() != null) {
            for (String reason : llmResult.getReasons()) {
                if (StringUtils.isNotBlank(reason)) {
                    reasons.add("[AI] " + reason);
                }
            }
        }
        if (!sameAction) {
            reasons.add("[AI] 与规则引擎结论不同，请以 AI 建议为主并人工复核");
        }
        merged.setReasons(reasons);

        if (sameAction) {
            merged.setConfidence(Math.max(ruleSuggestion.getConfidence(), merged.getConfidence()));
        }
        return merged;
    }

    /**
     * 合并规则预审与 LLM 预审：LLM 对 R1–R4 的结论优先，Q/C 项保留规则引擎证据片段
     */
    private List<CaptureRouteChecklistHint> mergeChecklistHints(List<CaptureRouteChecklistHint> ruleHintList,
                                                                List<CaptureRouteChecklistHint> llmHintList) {
        Map<String, CaptureRouteChecklistHint> mergedMap = new LinkedHashMap<String, CaptureRouteChecklistHint>();
        if (ruleHintList != null) {
            for (CaptureRouteChecklistHint ruleHint : ruleHintList) {
                if (ruleHint != null && StringUtils.isNotBlank(ruleHint.getId())) {
                    mergedMap.put(ruleHint.getId(), ruleHint);
                }
            }
        }
        if (llmHintList == null || llmHintList.isEmpty()) {
            return new ArrayList<CaptureRouteChecklistHint>(mergedMap.values());
        }

        for (CaptureRouteChecklistHint llmHint : llmHintList) {
            if (llmHint == null || StringUtils.isBlank(llmHint.getId())) {
                continue;
            }
            CaptureRouteChecklistHint ruleHint = mergedMap.get(llmHint.getId());
            mergedMap.put(llmHint.getId(), mergeSingleChecklistHint(ruleHint, llmHint));
        }
        return new ArrayList<CaptureRouteChecklistHint>(mergedMap.values());
    }

    /**
     * 单条检查项合并：LLM 覆盖 status/evidence，证据片段优先保留规则引擎产出
     */
    private CaptureRouteChecklistHint mergeSingleChecklistHint(CaptureRouteChecklistHint ruleHint,
                                                               CaptureRouteChecklistHint llmHint) {
        CaptureRouteChecklistHint mergedHint = new CaptureRouteChecklistHint();
        mergedHint.setId(llmHint.getId());
        mergedHint.setStatus(StringUtils.defaultIfBlank(llmHint.getStatus(),
                ruleHint == null ? "warn" : ruleHint.getStatus()));
        mergedHint.setEvidence(StringUtils.defaultIfBlank(llmHint.getEvidence(),
                ruleHint == null ? "" : ruleHint.getEvidence()));

        if (llmHint.getEvidenceSpans() != null && !llmHint.getEvidenceSpans().isEmpty()) {
            mergedHint.setEvidenceSpans(llmHint.getEvidenceSpans());
        } else if (ruleHint != null && ruleHint.getEvidenceSpans() != null) {
            mergedHint.setEvidenceSpans(ruleHint.getEvidenceSpans());
        }
        return mergedHint;
    }

    /**
     * 将 LLM 结果映射为对外建议结构
     */
    private CaptureRouteSuggestion mapLlmResult(CaptureRouteLlmResult llmResult) {
        CaptureRouteSuggestion suggestion = new CaptureRouteSuggestion();
        suggestion.setRecommendedAction(normalizeAction(llmResult.getRecommendedAction()));
        suggestion.setTargetKnowledgeType(normalizeKnowledgeType(llmResult.getTargetKnowledgeType(),
                suggestion.getRecommendedAction()));
        suggestion.setRecommendedRejectReason(llmResult.getRecommendedRejectReason());
        suggestion.setConfidence(llmResult.getConfidence() == null ? 0.7D : llmResult.getConfidence().doubleValue());
        suggestion.setChecklistHints(llmResult.getChecklistHints());
        return suggestion;
    }

    /**
     * 解析 LLM 返回 JSON，兼容 markdown 代码块包裹
     */
    private CaptureRouteLlmResult parseLlmResult(String rawContent) {
        if (StringUtils.isBlank(rawContent)) {
            throw new BusinessException(502, "LLM 返回内容为空");
        }
        String normalizedJson = unwrapJsonContent(rawContent.trim());
        try {
            CaptureRouteLlmResult llmResult = JSONUtil.toBean(normalizedJson, CaptureRouteLlmResult.class);
            if (StringUtils.isBlank(llmResult.getRecommendedAction())) {
                throw new BusinessException(502, "LLM 未返回 recommendedAction");
            }
            return llmResult;
        } catch (BusinessException businessException) {
            throw businessException;
        } catch (Exception exception) {
            throw new BusinessException(502, "LLM 返回 JSON 解析失败");
        }
    }

    /**
     * 去除 LLM 可能输出的 markdown 代码块标记
     */
    private String unwrapJsonContent(String rawContent) {
        if (rawContent.startsWith("```")) {
            int firstLineBreak = rawContent.indexOf('\n');
            if (firstLineBreak > 0) {
                rawContent = rawContent.substring(firstLineBreak + 1);
            }
            if (rawContent.endsWith("```")) {
                rawContent = rawContent.substring(0, rawContent.length() - 3);
            }
        }
        return rawContent.trim();
    }

    private String normalizeAction(String action) {
        if (StringUtils.isBlank(action)) {
            return MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE;
        }
        return action.trim();
    }

    /**
     * 补全 targetKnowledgeType，reject 时可为空
     */
    private String normalizeKnowledgeType(String targetKnowledgeType, String recommendedAction) {
        if (StringUtils.isNotBlank(targetKnowledgeType)) {
            return targetKnowledgeType;
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE.equals(recommendedAction)) {
            return MemoryConstants.KNOWLEDGE_TYPE_RULE;
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW.equals(recommendedAction)) {
            return MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW;
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_DECISION.equals(recommendedAction)) {
            return MemoryConstants.KNOWLEDGE_TYPE_DECISION;
        }
        if (MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE.equals(recommendedAction)) {
            return MemoryConstants.KNOWLEDGE_TYPE_EXPERIENCE;
        }
        return null;
    }

    private String formatActionLabel(String action) {
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE.equals(action)) {
            return "建议转为 Rule";
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW.equals(action)) {
            return "建议转为 Workflow";
        }
        if (MemoryConstants.REVIEW_ACTION_ROUTE_TO_DECISION.equals(action)) {
            return "建议转为 Decision";
        }
        if ("reject".equals(action)) {
            return "建议拒绝";
        }
        return "建议发布为经验";
    }

    private Map<String, Integer> countFactTypes(List<FactBlock> factBlocks) {
        Map<String, Integer> typeCount = new HashMap<String, Integer>();
        for (FactBlock factBlock : factBlocks) {
            if (factBlock == null || StringUtils.isBlank(factBlock.getType())) {
                continue;
            }
            String type = factBlock.getType();
            typeCount.put(type, getCount(typeCount, type) + 1);
        }
        return typeCount;
    }

    private int getCount(Map<String, Integer> typeCount, String type) {
        Integer count = typeCount.get(type);
        return count == null ? 0 : count.intValue();
    }

    private boolean containsAnyKeyword(String text, String[] keywords) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildAlternatives(String primaryAction) {
        List<String> alternatives = new ArrayList<String>();
        appendAlternative(alternatives, MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE, primaryAction);
        appendAlternative(alternatives, MemoryConstants.REVIEW_ACTION_APPROVE_RULE, primaryAction);
        appendAlternative(alternatives, MemoryConstants.REVIEW_ACTION_APPROVE_WORKFLOW, primaryAction);
        appendAlternative(alternatives, MemoryConstants.REVIEW_ACTION_APPROVE_DECISION, primaryAction);
        appendAlternative(alternatives, MemoryConstants.REVIEW_ACTION_ROUTE_TO_RULE, primaryAction);
        appendAlternative(alternatives, MemoryConstants.REVIEW_ACTION_ROUTE_TO_WORKFLOW, primaryAction);
        appendAlternative(alternatives, MemoryConstants.REVIEW_ACTION_ROUTE_TO_DECISION, primaryAction);
        if (!"reject".equals(primaryAction)) {
            alternatives.add("reject");
        }
        return alternatives;
    }

    private void appendAlternative(List<String> alternatives, String action, String primaryAction) {
        if (!action.equals(primaryAction)) {
            alternatives.add(action);
        }
    }

    /**
     * 将 Agent 声明的 knowledgeType 映射为 Review 推荐动作
     */
    private String resolveApproveActionForType(String knowledgeType) {
        if (MemoryConstants.KNOWLEDGE_TYPE_RULE.equals(knowledgeType)) {
            return MemoryConstants.REVIEW_ACTION_APPROVE_RULE;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_WORKFLOW.equals(knowledgeType)) {
            return MemoryConstants.REVIEW_ACTION_APPROVE_WORKFLOW;
        }
        if (MemoryConstants.KNOWLEDGE_TYPE_DECISION.equals(knowledgeType)) {
            return MemoryConstants.REVIEW_ACTION_APPROVE_DECISION;
        }
        return MemoryConstants.REVIEW_ACTION_APPROVE_EXPERIENCE;
    }
}
