package com.zhiyi.modelgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Model Gateway 配置：支持 local 直连与 remote 调 ai-gateway 两种模式
 */
@Data
@ConfigurationProperties(prefix = "zhiyi.model-gateway")
public class ModelGatewayProperties {

    /** local=Phase1 占位；remote=调 ai-gateway */
    private String mode = "local";

    /** ai-gateway 根地址，如 http://127.0.0.1:4502 */
    private String baseUrl = "http://127.0.0.1:4502";

    /** 调用方应用 id */
    private String appId = "zhiyi-server";

    /** 内部服务 Token */
    private String appToken = "";

    /** Capture Summarize 默认 profile */
    private String defaultChatProfile = "summary-default";

    /** Recall 向量默认 profile */
    private String defaultEmbeddingProfile = "embedding-default";

    /** Capture 归类 LLM 增强 profile */
    private String routeProfile = "route-default";

    /** 治理合并优化 LLM profile */
    private String mergeProfile = "summary-default";

    /** 规则引擎置信度低于此值时触发 LLM 增强 */
    private double routeLlmConfidenceThreshold = 0.75D;

    /** 是否启用 Capture 归类 LLM 增强 */
    private boolean routeLlmEnabled = true;

    private int connectTimeoutMillis = 5000;

    private int readTimeoutMillis = 120000;

    public boolean isRemoteMode() {
        return "remote".equalsIgnoreCase(mode);
    }
}
