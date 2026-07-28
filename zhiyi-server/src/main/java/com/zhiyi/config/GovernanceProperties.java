package com.zhiyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆治理自动化配置：定时增量扫描、质量校验、反馈触发与自动处置
 */
@Data
@ConfigurationProperties(prefix = "zhiyi.governance")
public class GovernanceProperties {

    /** 是否启用定时治理任务 */
    private boolean schedulerEnabled = false;

    /** 增量扫描 Cron 表达式，默认每天凌晨 3 点 */
    private String incrementalScanCron = "0 0 3 * * ?";

    /** 定时任务是否执行增量重复扫描 */
    private boolean incrementalScanEnabled = true;

    /** 定时任务是否执行质量校验扫描 */
    private boolean validateScanEnabled = true;

    /** Feedback outdated/wrong 累计达阈值是否自动生成治理工单 */
    private boolean feedbackTriggerEnabled = true;

    /** outdated + wrong 反馈次数达到该值时触发过时记忆工单 */
    private int outdatedFeedbackThreshold = 2;

    /** 是否允许高置信重复组自动处置（默认关闭，需工作空间显式开启） */
    private boolean autoResolveEnabled = false;

    /** 自动处置所需最低相似度 */
    private double autoResolveSimilarityThreshold = 0.85D;

    /** 手动/定时扫描默认重复阈值 */
    private double defaultSimilarityThreshold = 0.65D;
}
