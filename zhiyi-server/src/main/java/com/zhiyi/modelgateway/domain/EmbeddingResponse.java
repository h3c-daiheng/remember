package com.zhiyi.modelgateway.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 响应
 */
@Data
public class EmbeddingResponse {

    private List<List<Double>> embeddings = new ArrayList<List<Double>>();

    private String modelCode;

    private Integer totalTokens;

    private String requestId;
}
