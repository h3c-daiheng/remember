package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * Recall 执行元信息，用于操作日志与质量分析，不返回给 Agent
 */
@Data
public class RecallExecutionMeta {

    /** ContextQueryBuilder 生成的检索 Query 文本 */
    private String queryText;

    /** 向量/文本检索无结果时是否走了 fallback 候选 */
    private boolean fallbackUsed;

    /** 检索阶段返回的候选数量（fallback 前） */
    private int retrievalCandidateCount;

    /** 排序后进入截断前的候选数量 */
    private int rankedCandidateCount;

    /** 是否启用了图谱扩展 */
    private boolean graphExpandUsed;

    /** 图谱扩展新增的候选数量 */
    private int graphExpandedCandidateCount;
}
