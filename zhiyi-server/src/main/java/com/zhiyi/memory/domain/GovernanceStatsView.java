package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 治理统计摘要（Phase 3：质量与自动化漏斗）
 */
@Data
public class GovernanceStatsView {

    /** 待处理重复工单数 */
    private int openIssueCount;

    /** 已解决工单数 */
    private int resolvedIssueCount;

    /** 已忽略工单数 */
    private int dismissedIssueCount;

    /** 待处理碎片聚类工单数 */
    private int openFragmentIssueCount;

    /** 待处理不完整工单数 */
    private int openIncompleteIssueCount;

    /** 待处理过时工单数 */
    private int openOutdatedIssueCount;

    /** 待处理冲突工单数 */
    private int openConflictIssueCount;

    /** 已发布记忆总数 */
    private int publishedKnowledgeCount;

    /** 累计自动处置工单数 */
    private int autoResolvedIssueCount;

    /** 重复工单占已发布记忆比例（估算） */
    private double duplicateIssueRate;

    /** 最近一次全量/重复扫描时间 */
    private java.util.Date lastScanTime;

    /** 最近一次碎片扫描时间 */
    private java.util.Date lastFragmentScanTime;

    /** 最近一次增量扫描时间 */
    private java.util.Date lastIncrementalScanTime;

    /** 最近一次质量校验扫描时间 */
    private java.util.Date lastValidateScanTime;

    /** 治理漏斗 */
    private GovernanceFunnelView funnel;
}
