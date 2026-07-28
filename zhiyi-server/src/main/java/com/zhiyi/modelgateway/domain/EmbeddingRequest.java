package com.zhiyi.modelgateway.domain;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 请求
 */
@Data
@Builder
public class EmbeddingRequest {

    private String profile;

    private String modelCode;

    @Builder.Default
    private List<String> input = new ArrayList<String>();

    private String tenantId;
}
