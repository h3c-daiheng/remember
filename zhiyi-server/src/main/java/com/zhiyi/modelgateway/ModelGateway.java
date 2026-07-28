package com.zhiyi.modelgateway;

import com.zhiyi.modelgateway.domain.ChatCompletionRequest;
import com.zhiyi.modelgateway.domain.ChatCompletionResponse;

/**
 * 模型 Chat 门面：业务层通过 profile 调用 LLM，不关心具体厂商与密钥
 */
public interface ModelGateway {

    /**
     * 执行 Chat 补全
     *
     * @param request 含 profile、system/user 消息或 messages 列表
     * @return 模型生成内容与 token 用量
     */
    ChatCompletionResponse chat(ChatCompletionRequest request);

    /**
     * 简化调用：system + user 两段式 Prompt
     */
    ChatCompletionResponse chat(String profile, String systemPrompt, String userPrompt);
}
