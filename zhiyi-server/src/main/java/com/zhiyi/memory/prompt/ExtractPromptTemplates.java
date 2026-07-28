package com.zhiyi.memory.prompt;

import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.memory.domain.DocumentImportExtractRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档导入 LLM Prompt：System 正文外置 resources/prompts/extract/extract-system.md，User 在代码里拼装
 */
public final class ExtractPromptTemplates {

    private static final String SYSTEM_PROMPT = loadSystemPrompt();

    private ExtractPromptTemplates() {
    }

    /**
     * 文档抽取 System Prompt
     */
    public static String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 根据用户请求组装 User Prompt，包含 sourceDocument 与转化目标
     */
    public static String buildUserPrompt(DocumentImportExtractRequest extractRequest, String workspaceCode) {
        Map<String, Object> sourceDocument = new HashMap<String, Object>();
        sourceDocument.put("sourceType", StringUtils.defaultIfBlank(extractRequest.getSourceType(), "paste"));
        sourceDocument.put("title", StringUtils.defaultString(extractRequest.getTitle()));
        sourceDocument.put("fileName", StringUtils.defaultString(extractRequest.getFileName()));
        sourceDocument.put("body", extractRequest.getContent());
        if (StringUtils.isNotBlank(extractRequest.getModule())) {
            sourceDocument.put("moduleHint", extractRequest.getModule());
        }
        if (StringUtils.isNotBlank(extractRequest.getRepository())) {
            sourceDocument.put("repositoryHint", extractRequest.getRepository());
        }
        if (extractRequest.getTags() != null && !extractRequest.getTags().isEmpty()) {
            sourceDocument.put("tagsHint", extractRequest.getTags());
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请根据以下文档材料抽取 Capture 草稿 JSON。\n\n");
        userPrompt.append("workspace 编码：").append(StringUtils.defaultString(workspaceCode)).append("\n");
        userPrompt.append("repository：").append(StringUtils.defaultIfBlank(extractRequest.getRepository(), "未指定")).append("\n");
        userPrompt.append("module：").append(StringUtils.defaultIfBlank(extractRequest.getModule(), "请根据文档内容推断")).append("\n");
        userPrompt.append(buildTargetTypeInstruction(extractRequest.getTargetType()));
        userPrompt.append("\n--- sourceDocument ---\n");
        userPrompt.append(JSONUtil.toJsonPrettyStr(sourceDocument));
        userPrompt.append("\n--- end ---\n\n");
        userPrompt.append("要求：\n");
        userPrompt.append("1. artifacts 至少包含一条 document 类型的 origin 引用\n");
        userPrompt.append("2. tags 合并用户标签与文档推断的技术标签\n");
        userPrompt.append("3. 若材料不足以形成 Experience，诚实 submit=false\n");
        return userPrompt.toString();
    }

    /**
     * 根据用户指定的转化目标追加 Prompt 指令
     */
    private static String buildTargetTypeInstruction(String targetType) {
        if (StringUtils.isBlank(targetType) || "auto".equalsIgnoreCase(targetType)) {
            return "转化目标：自动识别最合适的知识类型\n";
        }
        if ("experience".equalsIgnoreCase(targetType)) {
            return "转化目标：优先抽取为 Experience，须含 observation + decision + outcome/evidence\n";
        }
        if ("decision".equalsIgnoreCase(targetType)) {
            return "转化目标：优先抽取为 Decision，侧重方案取舍与约束\n";
        }
        if ("rule".equalsIgnoreCase(targetType)) {
            return "转化目标：优先抽取为 Rule，facts 以 rule/constraint 为主，routeHint=route_to_rule\n";
        }
        if ("workflow".equalsIgnoreCase(targetType)) {
            return "转化目标：优先抽取为 Workflow，侧重步骤顺序，routeHint=route_to_workflow\n";
        }
        return "转化目标：自动识别最合适的知识类型\n";
    }

    /**
     * 启动类加载时读取 System Prompt 文件
     */
    private static String loadSystemPrompt() {
        String resourcePath = "prompts/extract/extract-system.md";
        InputStream inputStream = ExtractPromptTemplates.class.getClassLoader().getResourceAsStream(resourcePath);
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
