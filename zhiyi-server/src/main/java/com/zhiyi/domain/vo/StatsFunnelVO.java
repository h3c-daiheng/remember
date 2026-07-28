package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * Capture 闭环漏斗统计
 */
@Data
public class StatsFunnelVO {

    private Integer statsPeriodDays;

    /** Remember 成功次数 */
    private Integer rememberCount;

    /** 生成 Capture 草稿数 */
    private Integer draftCount;

    /** 已审草稿数（采纳 + 拒绝） */
    private Integer reviewedCount;

    /** 采纳草稿数 */
    private Integer approvedCount;

    /** 发布为经验数（草稿已关联 knowledge） */
    private Integer publishedCount;

    /** Remember → Draft 转化率 */
    private Double draftFromRememberRate;

    /** 已审 → 采纳 转化率 */
    private Double approvedFromReviewedRate;
}
