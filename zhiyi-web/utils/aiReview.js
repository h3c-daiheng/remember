/**
 * Capture AI Review 展示文案与状态映射
 */

/** AI Review 状态码 */
export const AI_REVIEW_STATUS = {
    QUEUED: 0,
    RUNNING: 1,
    DONE: 2,
    FAILED: 3,
}

/** AI Review 决策 */
export const AI_REVIEW_DECISION = {
    APPROVE: 'approve',
    REJECT: 'reject',
    ROUTE: 'route',
    ESCALATE_HUMAN: 'escalate_human',
}

/**
 * 根据草稿上的 AI Review 字段生成列表/抽屉徽标文案
 */
export function resolveAiReviewBadge(draft) {
    const status = draft?.aiReviewStatus
    const decision = draft?.aiReviewDecision

    if (status === AI_REVIEW_STATUS.QUEUED || status === AI_REVIEW_STATUS.RUNNING) {
        return {
            label: 'AI 审核中',
            tone: 'processing',
        }
    }
    if (status === AI_REVIEW_STATUS.FAILED) {
        return {
            label: 'AI 失败·需人工',
            tone: 'danger',
        }
    }
    if (decision === AI_REVIEW_DECISION.ESCALATE_HUMAN) {
        return {
            label: '需人工',
            tone: 'warning',
        }
    }
    if (decision === AI_REVIEW_DECISION.APPROVE) {
        return {
            label: 'AI 已通过',
            tone: 'success',
        }
    }
    if (decision === AI_REVIEW_DECISION.REJECT) {
        return {
            label: 'AI 已拒绝',
            tone: 'danger',
        }
    }
    if (decision === AI_REVIEW_DECISION.ROUTE) {
        return {
            label: 'AI 已路由',
            tone: 'success',
        }
    }
    return null
}

/**
 * 决策中文标签
 */
export function formatAiReviewDecision(decision) {
    const labelMap = {
        [AI_REVIEW_DECISION.APPROVE]: '自动采纳',
        [AI_REVIEW_DECISION.REJECT]: '自动拒绝',
        [AI_REVIEW_DECISION.ROUTE]: '自动路由',
        [AI_REVIEW_DECISION.ESCALATE_HUMAN]: '转人工',
    }
    return labelMap[decision] || decision || '—'
}

/** 状态中文标签（记录列表用） */
export const AI_REVIEW_STATUS_LABELS = {
    [AI_REVIEW_STATUS.QUEUED]: '排队中',
    [AI_REVIEW_STATUS.RUNNING]: '进行中',
    [AI_REVIEW_STATUS.DONE]: '已完成',
    [AI_REVIEW_STATUS.FAILED]: '失败',
}

/**
 * 格式化 AI Review 运行状态
 */
export function formatAiReviewStatus(status) {
    if (status === undefined || status === null) {
        return '—'
    }
    return AI_REVIEW_STATUS_LABELS[status] || String(status)
}

/**
 * 根据单次审查记录生成徽标（独立记录页列表）
 */
export function resolveAiReviewRecordBadge(record) {
    const status = record?.status
    const decision = record?.decision
    if (status === AI_REVIEW_STATUS.QUEUED || status === AI_REVIEW_STATUS.RUNNING) {
        return { label: 'AI 审核中', tone: 'processing' }
    }
    if (status === AI_REVIEW_STATUS.FAILED) {
        return { label: '失败', tone: 'danger' }
    }
    if (decision === AI_REVIEW_DECISION.ESCALATE_HUMAN) {
        return { label: '转人工', tone: 'warning' }
    }
    if (decision === AI_REVIEW_DECISION.APPROVE) {
        return { label: '自动采纳', tone: 'success' }
    }
    if (decision === AI_REVIEW_DECISION.REJECT) {
        return { label: '自动拒绝', tone: 'danger' }
    }
    if (decision === AI_REVIEW_DECISION.ROUTE) {
        return { label: '自动路由', tone: 'success' }
    }
    if (status === AI_REVIEW_STATUS.DONE) {
        return { label: '已完成', tone: 'success' }
    }
    return null
}

/** Capture 草稿待确认状态（与后端 REVIEW_PENDING 对齐） */
export const CAPTURE_DRAFT_REVIEW_PENDING = 0

/**
 * 关联草稿是否仍待确认（可重跑 / 去确认）
 * 使用 Number 比较，兼容接口返回字符串 "0"
 */
export function isDraftStillPending(draftReviewStatus) {
    if (draftReviewStatus === undefined || draftReviewStatus === null || draftReviewStatus === '') {
        return false
    }
    return Number(draftReviewStatus) === CAPTURE_DRAFT_REVIEW_PENDING
}
