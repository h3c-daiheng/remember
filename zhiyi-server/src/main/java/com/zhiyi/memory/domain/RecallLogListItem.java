package com.zhiyi.memory.domain;

import lombok.Data;

import java.util.Date;

/**
 * Recall / Search 操作日志列表项，供 Web「召回记录」页分页展示
 */
@Data
public class RecallLogListItem {

    private Long logId;

    private Date createTime;

    /** recall / search */
    private String operation;

    /** 1-成功 0-失败 */
    private Integer success;

    private Integer latencyMs;

    private String recallSession;

    private String traceId;

    /** api_key / jwt */
    private String authType;

    private Long apiKeyId;

    /** 从 requestJson.task 解析的任务描述 */
    private String task;

    /** 返回条数（成功时有值） */
    private Integer itemCount;

    /** Top1 命中标题 */
    private String topTitle;

    /** 是否使用了检索 Fallback */
    private Boolean fallbackUsed;

    /** 失败时的错误信息 */
    private String errorMessage;
}
