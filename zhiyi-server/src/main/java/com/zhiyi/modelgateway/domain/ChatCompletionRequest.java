package com.zhiyi.modelgateway.domain;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat 补全请求，与 ai-gateway 契约对齐
 */
@Data
@Builder
public class ChatCompletionRequest {

    /** 业务 profile，如 summary-default */
    private String profile;

    /** 直指定 modelCode，profile 为空时使用 */
    private String modelCode;

    /** 简化字段：system 角色 Prompt */
    private String systemPrompt;

    /** 简化字段：user 角色 Prompt */
    private String userPrompt;

    /** 完整 messages，非空时优先于 systemPrompt/userPrompt */
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<ChatMessage>();

    private Double temperature;

    private Integer maxTokens;

    /** json_object / text */
    private String responseFormat;

    /** 租户或工作空间，写入网关 X-Tenant-Id */
    private String tenantId;

    /**
     * 单条对话消息
     */
    @Data
    @Builder
    public static class ChatMessage {
        private String role;
        private String content;
    }
}
