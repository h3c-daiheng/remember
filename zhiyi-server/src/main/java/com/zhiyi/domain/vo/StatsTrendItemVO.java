package com.zhiyi.domain.vo;

import lombok.Data;

/**
 * 单日召回与反馈趋势项
 */
@Data
public class StatsTrendItemVO {

    /** 统计日期，格式 yyyy-MM-dd */
    private String date;

    /** Recall + Search 成功次数 */
    private Integer recallCount;

    /** Remember 成功次数 */
    private Integer rememberCount;

    /** Feedback 次数（含 operation_log 中 feedback 操作） */
    private Integer feedbackCount;

    /** helpful / used 反馈次数 */
    private Integer helpfulCount;
}
