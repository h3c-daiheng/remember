package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 单日用量项
 */
@Data
public class StatsUsageDailyItemVO {

    /** 统计日期 yyyy-MM-dd */
    private String date;

    private Integer recallCount;

    private Integer rememberCount;

    private Integer feedbackCount;
}
