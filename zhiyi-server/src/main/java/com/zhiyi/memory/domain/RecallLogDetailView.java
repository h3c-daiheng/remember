package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Recall 操作日志详情视图，供 Web 召回调试与质量分析
 */
@Data
public class RecallLogDetailView {

    private Long logId;

    private String traceId;

    private String recallSession;

    /** recall / search */
    private String operation;

    private Date createTime;

    private Integer latencyMs;

    /** 完整 RecallContext 快照 */
    private RecallContext requestContext;

    private String queryText;

    private boolean fallbackUsed;

    private int retrievalCandidateCount;

    private int rankedCandidateCount;

    private int itemCount;

    private List<RecallItemSummary> items;

    /** promptBlock 字符长度 */
    private int promptBlockLength;

    /** promptBlock 预览，便于核对 Agent 实际注入内容 */
    private String promptBlockPreview;

    @Data
    public static class RecallItemSummary {

        private Integer rank;

        private Long knowledgeId;

        private String title;

        private String knowledgeType;

        private Double score;

        private Map<String, Double> scoreBreakdown;
    }
}
