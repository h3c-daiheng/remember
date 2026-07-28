package com.zhiyi.modelgateway;

import com.zhiyi.modelgateway.domain.EmbeddingRequest;
import com.zhiyi.modelgateway.domain.EmbeddingResponse;

import java.util.List;

/**
 * Embedding 门面：为 Recall 向量索引与检索提供统一向量化能力
 */
public interface EmbeddingGateway {

    /**
     * 批量文本向量化
     */
    EmbeddingResponse embed(EmbeddingRequest request);

    /**
     * 单条文本向量化
     */
    List<Double> embedOne(String profile, String text);
}
