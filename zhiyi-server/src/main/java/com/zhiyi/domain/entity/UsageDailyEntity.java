package com.zhiyi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 日用量统计实体，对应 usage_daily 表，供计费与限流
 */
@Data
@TableName("usage_daily")
public class UsageDailyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 workspace.id */
    private String workspaceId;

    /** 统计日期 */
    private Date usageDate;

    /** Recall + Search 次数 */
    private Integer recallCount;

    /** Remember 次数 */
    private Integer rememberCount;

    /** Feedback 次数 */
    private Integer feedbackCount;

    /** Embedding token 估算 */
    private Long embeddingTokens;

    /** 创建时间 */
    private Date createTime;
}
