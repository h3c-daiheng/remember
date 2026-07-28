package com.zhiyi.memory.prompt;

import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.domain.CaptureRouteSuggestion;
import com.zhiyi.memory.domain.CaptureSimilarKnowledgeHint;
import com.zhiyi.memory.domain.KnowledgeDraftContent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Capture 归类 LLM Prompt：System 正文外置 resources/prompts/route/route-system.md
 */
public final class RoutePromptTemplates {

    private static final String SYSTEM_PROMPT = loadSystemPrompt();

    private RoutePromptTemplates() {
    }

    /**
     * 归类建议 System Prompt
     */
    public static String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 组装 User Prompt：草稿 Fact、规则引擎基线、相似已有知识
     */
    public static String buildUserPrompt(KnowledgeDraftContent draftContent,
                                         CaptureRouteSuggestion ruleSuggestion,
                                         List<CaptureSimilarKnowledgeHint> similarKnowledgeList) {
        Map<String, Object> draftPayload = new HashMap<String, Object>();
        draftPayload.put("title", StringUtils.defaultString(draftContent.getTitle()));
        draftPayload.put("module", StringUtils.defaultString(draftContent.getModule()));
        draftPayload.put("repository", StringUtils.defaultString(draftContent.getRepository()));
        draftPayload.put("project", StringUtils.defaultString(draftContent.getProject()));
        draftPayload.put("tags", draftContent.getTags());
        draftPayload.put("facts", draftContent.getFacts());

        Map<String, Object> ruleBaseline = new HashMap<String, Object>();
        ruleBaseline.put("recommendedAction", ruleSuggestion.getRecommendedAction());
        ruleBaseline.put("targetKnowledgeType", ruleSuggestion.getTargetKnowledgeType());
        ruleBaseline.put("confidence", ruleSuggestion.getConfidence());
        ruleBaseline.put("recommendedRejectReason", ruleSuggestion.getRecommendedRejectReason());
        ruleBaseline.put("reasons", ruleSuggestion.getReasons());

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请分析以下 Capture 草稿，输出归类建议 JSON。\n\n");
        userPrompt.append("--- captureDraft ---\n");
        userPrompt.append(JSONUtil.toJsonPrettyStr(draftPayload));
        userPrompt.append("\n--- end ---\n\n");
        userPrompt.append("--- ruleEngineBaseline ---\n");
        userPrompt.append(JSONUtil.toJsonPrettyStr(ruleBaseline));
        userPrompt.append("\n--- end ---\n\n");
        userPrompt.append("规则引擎置信度较低或与内容信号可能冲突，请结合草稿语义给出最终建议。\n\n");

        if (similarKnowledgeList != null && !similarKnowledgeList.isEmpty()) {
            userPrompt.append("--- similarKnowledgeInWorkspace ---\n");
            userPrompt.append(JSONUtil.toJsonPrettyStr(similarKnowledgeList));
            userPrompt.append("\n--- end ---\n\n");
            userPrompt.append("请重点用 similarKnowledge 辅助 R4 是否与已有 Rule 重复。\n");
        } else {
            userPrompt.append("工作空间内未检索到高相似已有知识。\n");
        }

        userPrompt.append("\n要求：checklistHints 必须包含 R1、R2、R3、R4 四项。");
        return userPrompt.toString();
    }

    /**
     * 启动类加载时读取 System Prompt 文件
     */
    private static String loadSystemPrompt() {
        String resourcePath = "prompts/route/route-system.md";
        InputStream inputStream = RoutePromptTemplates.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new BusinessException(500, "Prompt 文件不存在：" + resourcePath);
        }
        try {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException ioException) {
            throw new BusinessException(500, "Prompt 文件读取失败：" + resourcePath);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // 忽略关闭异常
            }
        }
    }
}
