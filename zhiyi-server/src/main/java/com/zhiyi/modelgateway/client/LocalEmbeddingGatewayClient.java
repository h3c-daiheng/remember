package com.zhiyi.modelgateway.client;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.Result;
import com.zhiyi.modelgateway.EmbeddingGateway;
import com.zhiyi.modelgateway.config.ModelGatewayProperties;
import com.zhiyi.modelgateway.domain.EmbeddingRequest;
import com.zhiyi.modelgateway.domain.EmbeddingResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Embedding 客户端：remote 模式 HTTP 调 ai-gateway，local 模式占位
 */
@Component
public class LocalEmbeddingGatewayClient implements EmbeddingGateway {

    private static final String EMBEDDING_PATH = "/v1/embeddings";

    private final ModelGatewayProperties modelGatewayProperties;

    public LocalEmbeddingGatewayClient(ModelGatewayProperties modelGatewayProperties) {
        this.modelGatewayProperties = modelGatewayProperties;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (!modelGatewayProperties.isRemoteMode()) {
            throw notConfiguredException();
        }
        if (request.getInput() == null || request.getInput().isEmpty()) {
            throw new BusinessException(400, "Embedding input 不能为空");
        }
        if (StringUtils.isBlank(request.getProfile())) {
            request.setProfile(modelGatewayProperties.getDefaultEmbeddingProfile());
        }

        String requestBody = JSONUtil.toJsonStr(request);
        String baseUrl = modelGatewayProperties.getBaseUrl();
        HttpResponse httpResponse = ModelGatewayHttpExecutor.execute(
                HttpRequest.post(buildUrl(EMBEDDING_PATH))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + modelGatewayProperties.getAppToken())
                        .header("X-App-Id", modelGatewayProperties.getAppId())
                        .header("X-Tenant-Id", StringUtils.defaultString(request.getTenantId()))
                        .setConnectionTimeout(modelGatewayProperties.getConnectTimeoutMillis())
                        .setReadTimeout(modelGatewayProperties.getReadTimeoutMillis())
                        .body(requestBody),
                baseUrl,
                "Embedding"
        );

        if (httpResponse.getStatus() >= 400) {
            throw ModelGatewayHttpExecutor.buildHttpStatusException(httpResponse, "Embedding");
        }

        Result<EmbeddingResponse> result = JSONUtil.toBean(
                httpResponse.body(),
                new TypeReference<Result<EmbeddingResponse>>() {
                },
                false
        );
        if (result == null || result.getCode() != 0) {
            String message = result == null ? "响应解析失败" : result.getMessage();
            throw new BusinessException(502, "ai-gateway Embedding 返回错误：" + message);
        }
        return result.getData();
    }

    @Override
    public List<Double> embedOne(String profile, String text) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .profile(profile)
                .input(Collections.singletonList(text))
                .build();
        EmbeddingResponse response = embed(request);
        if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            throw new BusinessException(502, "ai-gateway 未返回向量结果");
        }
        return response.getEmbeddings().get(0);
    }

    private String buildUrl(String path) {
        String baseUrl = StringUtils.removeEnd(modelGatewayProperties.getBaseUrl(), "/");
        return baseUrl + path;
    }

    private BusinessException notConfiguredException() {
        return new BusinessException(501, "Embedding 未配置：请设置 zhiyi.model-gateway.mode=remote 并配置 base-url");
    }
}
