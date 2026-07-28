package com.zhiyi.modelgateway.domain;

import lombok.Data;

/**
 * Chat 补全响应
 */
@Data
public class ChatCompletionResponse {

    private String content;

    private String modelCode;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private String requestId;
}
