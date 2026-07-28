package com.zhiyi.modelgateway.client;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import com.zhiyi.modelgateway.ModelGateway;
import com.zhiyi.modelgateway.config.ModelGatewayProperties;
import com.zhiyi.modelgateway.domain.ChatCompletionRequest;
import com.zhiyi.modelgateway.domain.ChatCompletionResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model Gateway 客户端：remote 模式 HTTP 调 ai-gateway，local 模式占位
 */
@Component
public class LocalModelGatewayClient implements ModelGateway {

    private static final String CHAT_PATH = "/v1/chat/completions";

    private final ModelGatewayProperties modelGatewayProperties;

    public LocalModelGatewayClient(ModelGatewayProperties modelGatewayProperties) {
        this.modelGatewayProperties = modelGatewayProperties;
    }

    @Override
    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        if (!modelGatewayProperties.isRemoteMode()) {
            throw notConfiguredException();
        }

        ChatCompletionRequest normalizedRequest = normalizeRequest(request);
        String requestBody = JSONUtil.toJsonStr(buildGatewayPayload(normalizedRequest));
        String baseUrl = modelGatewayProperties.getBaseUrl();
        HttpResponse httpResponse = ModelGatewayHttpExecutor.execute(
                HttpRequest.post(buildUrl(CHAT_PATH))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + modelGatewayProperties.getAppToken())
                        .header("X-App-Id", modelGatewayProperties.getAppId())
                        .header("X-Tenant-Id", StringUtils.defaultString(normalizedRequest.getTenantId()))
                        .setConnectionTimeout(modelGatewayProperties.getConnectTimeoutMillis())
                        .setReadTimeout(modelGatewayProperties.getReadTimeoutMillis())
                        .body(requestBody),
                baseUrl,
                "Chat"
        );

        if (httpResponse.getStatus() >= 400) {
            throw ModelGatewayHttpExecutor.buildHttpStatusException(httpResponse, "Chat");
        }

        Result<ChatCompletionResponse> result = JSONUtil.toBean(
                httpResponse.body(),
                new TypeReference<Result<ChatCompletionResponse>>() {
                },
                false
        );
        if (result == null || result.getCode() != 0) {
            String message = result == null ? "响应解析失败" : result.getMessage();
            throw new BusinessException(502, "ai-gateway 返回错误：" + message);
        }
        return result.getData();
    }

    @Override
    public ChatCompletionResponse chat(String profile, String systemPrompt, String userPrompt) {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .profile(profile)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();
        return chat(request);
    }

    /**
     * 补全 profile 与 messages 列表
     */
    private ChatCompletionRequest normalizeRequest(ChatCompletionRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            if (StringUtils.isBlank(request.getProfile())) {
                request.setProfile(modelGatewayProperties.getDefaultChatProfile());
            }
            return request;
        }
        List<ChatCompletionRequest.ChatMessage> messages = new ArrayList<ChatCompletionRequest.ChatMessage>();
        if (StringUtils.isNotBlank(request.getSystemPrompt())) {
            messages.add(ChatCompletionRequest.ChatMessage.builder()
                    .role("system")
                    .content(request.getSystemPrompt())
                    .build());
        }
        if (StringUtils.isNotBlank(request.getUserPrompt())) {
            messages.add(ChatCompletionRequest.ChatMessage.builder()
                    .role("user")
                    .content(request.getUserPrompt())
                    .build());
        }
        if (messages.isEmpty()) {
            throw new BusinessException(400, "Chat 请求缺少 messages 或 system/user Prompt");
        }
        request.setMessages(messages);
        if (StringUtils.isBlank(request.getProfile())) {
            request.setProfile(modelGatewayProperties.getDefaultChatProfile());
        }
        return request;
    }

    /**
     * 转为 ai-gateway 侧 JSON 结构
     */
    private Map<String, Object> buildGatewayPayload(ChatCompletionRequest request) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("profile", request.getProfile());
        payload.put("modelCode", request.getModelCode());
        payload.put("temperature", request.getTemperature());
        payload.put("maxTokens", request.getMaxTokens());
        payload.put("responseFormat", request.getResponseFormat());
        List<Map<String, String>> messageList = new ArrayList<Map<String, String>>();
        for (ChatCompletionRequest.ChatMessage message : request.getMessages()) {
            Map<String, String> messageMap = new HashMap<String, String>();
            messageMap.put("role", message.getRole());
            messageMap.put("content", message.getContent());
            messageList.add(messageMap);
        }
        payload.put("messages", messageList);
        return payload;
    }

    private String buildUrl(String path) {
        String baseUrl = StringUtils.removeEnd(modelGatewayProperties.getBaseUrl(), "/");
        return baseUrl + path;
    }

    private BusinessException notConfiguredException() {
        return new BusinessException(501, "LLM 未配置：请设置 zhiyi.model-gateway.mode=remote 并配置 base-url");
    }
}
