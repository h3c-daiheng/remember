package com.zhiyi.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 工作空间月用量统计，含日序列与配额
 */
@Data
public class StatsUsageVO {

    /** 统计月份 yyyy-MM */
    private String month;

    /** 本月 Recall + Search 累计 */
    private Integer recallUsed;

    /** 本月 Remember 累计 */
    private Integer rememberUsed;

    /** 本月 Feedback 累计 */
    private Integer feedbackUsed;

    /** 组织月 Recall 配额 */
    private Integer recallQuota;

    /** 当月日用量序列 */
    private List<StatsUsageDailyItemVO> dailyList;
}
