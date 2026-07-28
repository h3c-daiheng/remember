package com.zhiyi.memory.domain;

import lombok.Data;

/**
 * 治理工单处置请求
 */
@Data
public class GovernanceResolveRequest {

    /** 处置动作：keep_primary_deprecate_others / merge_facts / dismiss */
    private String action;

    /** 指定主版本 ID，缺省使用工单推荐主版本 */
    private Long primaryKnowledgeId;

    /** 处置备注 */
    private String comment;

    /** merge_optimize 时携带的合并确认内容 */
    private GovernanceMergeConfirmRequest mergeConfirm;
}
