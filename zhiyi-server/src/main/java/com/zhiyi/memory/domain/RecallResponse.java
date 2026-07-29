package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Recall API 响应
 */
@Data
public class RecallResponse {

    /** Recall 会话 ID，供 Feedback 关联 */
    private String sessionId;

    private List<RecallItem> items;

    /** 组装好的 prompt 文本块，可直接注入 Agent 上下文 */
    private String promptBlock;

    @Data
    public static class RecallItem {

        private Long knowledgeId;

        private String title;

        /** 知识类型：rule / workflow / experience */
        private String knowledgeType;

        private List<FactBlock> facts;

        private List<ArtifactDto> artifacts;

        private Double score;

        private Map<String, Double> scoreBreakdown;

        /** 可靠性档位：reliable / uncertain / low */
        private String reliability;

        /** 触发降档的因子说明，供 Agent 判断为何存疑 */
        private List<String> reliabilityReason;
    }
}
