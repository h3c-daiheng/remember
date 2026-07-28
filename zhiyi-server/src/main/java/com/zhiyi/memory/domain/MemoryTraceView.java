package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Memory 闭环追踪视图：串联 Remember → Capture → 发布 → Recall 命中
 */
@Data
public class MemoryTraceView {

    /** 查询入口：draft / knowledge / recall */
    private String entryType;

    /** 查询入口 ID */
    private Long entryId;

    /** Capture 草稿摘要 */
    private CaptureDraftView captureDraft;

    /** Remember 产生的 System Event 摘要 */
    private SystemEventSummary systemEvent;

    /** 关联知识对象列表（含路由产出与直接发布） */
    private List<KnowledgeTraceItem> knowledgeList;

    /** 命中上述 knowledge 的 Recall 记录 */
    private List<RecallHitItem> recallHits;

    /**
     * System Event 摘要，避免 trace 接口返回完整 payload
     */
    @Data
    public static class SystemEventSummary {

        private Long eventId;

        private String eventType;

        private String actor;

        private String repository;

        private String module;

        private Integer processStatus;

        private Date eventTime;

        private Date createTime;
    }

    /**
     * 知识对象在 trace 中的摘要
     */
    @Data
    public static class KnowledgeTraceItem {

        private Long knowledgeId;

        private String knowledgeType;

        private String title;

        private Integer lifecycleStatus;

        private Long sourceCaptureDraftId;

        private Integer recallCount;

        private Date createTime;

        private Date updateTime;
    }

    /**
     * Recall 命中记录：某次 recall 返回结果中包含目标 knowledge
     */
    @Data
    public static class RecallHitItem {

        private Long logId;

        private String traceId;

        private String recallSession;

        /** 在该次 Recall 结果中的排名，从 1 开始 */
        private Integer rank;

        private Long knowledgeId;

        private String title;

        private Double score;

        /** 排序维度分解，便于分析召回质量 */
        private Map<String, Double> scoreBreakdown;

        private String task;

        private String module;

        private String repository;

        /** 检索 Query 文本摘要 */
        private String queryText;

        /** 是否走了 fallback 候选 */
        private Boolean fallbackUsed;

        /** 该次 Recall 返回条数 */
        private Integer itemCount;

        private Date createTime;
    }
}
