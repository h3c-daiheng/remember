package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 工作空间统计概览，供 /stats 页顶部指标卡展示
 */
@Data
public class StatsOverviewVO {

    /** 本周 helpful / used 反馈数（北极星指标） */
    private Integer weeklyHelpfulRecalls;

    /** helpful 占比，0~1，近 statsPeriodDays 天 */
    private Double helpfulRate;

    /** 已发布经验数 */
    private Integer publishedCount;

    /** 待确认 Capture 草稿数 */
    private Integer pendingDraftCount;

    /** 已失效经验数 */
    private Integer deprecatedCount;

    /** 草稿采纳率，近 30 天 review_status=已采纳 / 已审草稿 */
    private Double draftToPublishedRate;

    /** Remember 转草稿率，近 30 天 capture_draft 数 / remember 成功数 */
    private Double rememberToDraftRate;

    /** 近 statsPeriodDays 天 Recall + Search 成功次数 */
    private Integer recallCount7d;

    /** 近 statsPeriodDays 天 Remember 成功次数 */
    private Integer rememberCount7d;

    /** 近 statsPeriodDays 天 Feedback 次数 */
    private Integer feedbackCount7d;

    /** 概览统计窗口天数 */
    private Integer statsPeriodDays;
}
