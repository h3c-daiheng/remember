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

    /**
     * 相似交叉验证阈值：LLM 建议 approve 但存在达到此相似度的已发布记忆时，
     * 视为疑似重复转人工；亦用于结论类型与相似记忆矛盾的判定
     */
    private double similarThreshold = 0.65D;

    /** 极高相似重复门禁：达到此相似度无论 LLM 结论如何都直接转人工 */
    private double similarDuplicateThreshold = 0.85D;

    /** 是否允许高置信度时自动 route 并发布 */
    private boolean autoRouteEnabled = true;

    /** 自动执行（approve/route）所需最低置信度；低于则转人工 */
    private double autoExecuteConfidenceThreshold = 0.75D;
}
