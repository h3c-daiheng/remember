package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 更新工作空间治理配置请求
 */
@Data
public class GovernanceConfigUpdateRequest {

    /** 是否启用高置信重复自动处置 */
    private Boolean autoResolveEnabled;

    /** 自动处置相似度阈值 */
    private Double autoResolveSimilarityThreshold;
}
