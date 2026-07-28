package com.zhiyi.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Memory API 操作追踪日志，对应 memory_operation_log 表
 */
@Data
@TableName("memory_operation_log")
public class MemoryOperationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作空间主键 */
    private String workspaceId;

    /** 操作类型：remember / recall / feedback / search */
    private String operation;

    /** 调用链 trace ID，可与 MCP X-Trace-Id 对齐 */
    private String traceId;

    /** Recall 会话 ID，便于关联 feedback */
    private String recallSession;

    /** API Key 主键，JWT 鉴权时为 null */
    private Long apiKeyId;

    /** 鉴权方式：api_key / jwt */
    private String authType;

    /** 请求快照 JSON */
    private String requestJson;

    /** 响应摘要 JSON，含 sessionId、draftId、knowledgeId 列表等 */
    private String responseSummaryJson;

    /** 1-成功 0-失败 */
    private Integer success;

    /** 失败时的错误信息 */
    private String errorMessage;

    /** 接口耗时毫秒 */
    private Integer latencyMs;

    private Date createTime;
}
