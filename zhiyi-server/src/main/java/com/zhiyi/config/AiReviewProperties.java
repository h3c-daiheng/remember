package com.zhiyi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Capture AI Review 配置：提交后自动审核与相似记忆门禁
 */
@Data
@ConfigurationProperties(prefix = "zhiyi.ai-review")
public class AiReviewProperties {

    /** 是否启用提交后 AI Review */
    private boolean enabled = true;

    /** 相似记忆门禁阈值，达到则转人工 */
    private double similarThreshold = 0.65D;

    /** 是否允许高置信度时自动 route 并发布 */
    private boolean autoRouteEnabled = true;

    /** 自动执行（approve/reject/route）所需最低置信度 */
    private double autoExecuteConfidenceThreshold = 0.75D;
}
