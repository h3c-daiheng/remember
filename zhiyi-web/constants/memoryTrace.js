/**
 * Memory 闭环追踪常量：Capture Review 动作、链路步骤标签
 */

/** Capture Review 动作中文标签 */
export const CAPTURE_REVIEW_ACTION_LABELS = {
    approve_experience: '提交为经验',
    approve_rule: '提交为 Rule',
    approve_workflow: '提交为 Workflow',
    approve_decision: '提交为 Decision',
    reject: '已拒绝',
    route_to_rule: '提交为 Rule',
    route_to_workflow: '提交为 Workflow',
    route_to_decision: '提交为 Decision',
}

/** Capture 草稿审核状态中文标签 */
export const CAPTURE_REVIEW_STATUS_LABELS = {
    0: '待确认',
    1: '已采纳',
    2: '已拒绝',
}

/** 闭环追踪时间线步骤定义 */
export const MEMORY_TRACE_STEPS = [
    { key: 'remember', label: 'Submit', description: 'Agent 提交记忆草稿' },
    { key: 'capture', label: 'Capture', description: '生成草稿与 Review' },
    { key: 'publish', label: '发布', description: '经验或 Rule 入库' },
    { key: 'recall', label: 'Recall 命中', description: '任务召回验证' },
]

/** Recall 排序维度中文标签，便于质量分析展示 */
export const RECALL_SCORE_BREAKDOWN_LABELS = {
    taskMatch: 'Task Match',
    similarity: '相似度',
    trust: '可信度',
    freshness: '时效',
    feedback: '反馈',
    typeBoost: '类型加权',
}
