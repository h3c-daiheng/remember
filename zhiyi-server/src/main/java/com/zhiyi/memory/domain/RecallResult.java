package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * Recall 引擎完整结果：Agent 响应 + 质量分析用执行元信息
 */
@Data
public class RecallResult {

    private RecallResponse response;

    private RecallExecutionMeta executionMeta;
}
