package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * LLM 抽取结果 JSON 映射，与 GitHub 导入指南输出 Schema 对齐
 */
@Data
public class DocumentExtractLlmResult {

    private Boolean submit;

    private String skipReason;

    private String recommendedKnowledgeType;

    private Double confidence;

    private String routeHint;

    private Map<String, Object> memoryRememberRequest;

    /**
     * 从 memoryRememberRequest.payload 读取 facts 列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> readFacts() {
        if (memoryRememberRequest == null) {
            return null;
        }
        Object payloadObject = memoryRememberRequest.get("payload");
        if (!(payloadObject instanceof Map)) {
            return null;
        }
        Map<String, Object> payload = (Map<String, Object>) payloadObject;
        Object factsObject = payload.get("facts");
        if (!(factsObject instanceof List)) {
            return null;
        }
        return (List<Map<String, Object>>) factsObject;
    }
}
