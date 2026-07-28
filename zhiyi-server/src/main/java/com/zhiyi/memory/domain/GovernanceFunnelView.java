package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 治理漏斗统计：扫描 → 发现 → 处置
 */
@Data
public class GovernanceFunnelView {

    /** 已完成扫描批次数 */
    private int totalScanBatchCount;

    /** 累计创建工单数 */
    private int totalIssueCreatedCount;

    /** 累计已解决工单数 */
    private int totalIssueResolvedCount;

    /** 累计已忽略工单数 */
    private int totalIssueDismissedCount;

    /** 累计自动处置工单数 */
    private int totalAutoResolvedCount;
}
