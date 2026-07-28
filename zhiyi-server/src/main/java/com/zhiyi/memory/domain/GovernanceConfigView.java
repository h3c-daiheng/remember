package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 工作空间治理配置视图
 */
@Data
public class GovernanceConfigView {

    /** 是否启用高置信重复自动处置 */
    private boolean autoResolveEnabled;

    /** 自动处置相似度阈值 */
    private double autoResolveSimilarityThreshold;

    /** 全局默认可自动处置（工作空间未覆盖时） */
    private boolean globalAutoResolveEnabled;

    /** 全局默认自动处置阈值 */
    private double globalAutoResolveSimilarityThreshold;

    /** 是否启用定时增量扫描 */
    private boolean schedulerEnabled;

    /** 是否启用 Feedback 触发过时工单 */
    private boolean feedbackTriggerEnabled;

    /** outdated + wrong 反馈触发阈值 */
    private int outdatedFeedbackThreshold;
}
