/**
 * Memory 闭环追踪纯函数：路径构建、状态文案等
 */
import { KNOWLEDGE_LIFECYCLE, KNOWLEDGE_TYPE_LABELS } from '~/constants/knowledge'
import {
    CAPTURE_REVIEW_ACTION_LABELS,
    CAPTURE_REVIEW_STATUS_LABELS,
    RECALL_SCORE_BREAKDOWN_LABELS,
} from '~/constants/memoryTrace'

/**
 * 构建 Capture 草稿下拉选项展示文案
 */
export function formatCaptureDraftOptionLabel(draft) {
    if (!draft) {
        return ''
    }
    const title = draft.draftContent?.title || '未命名草稿'
    const statusLabel = CAPTURE_REVIEW_STATUS_LABELS[draft.reviewStatus] || ''
    const statusSuffix = statusLabel ? ` · ${statusLabel}` : ''
    return `${title} (#${draft.id}${statusSuffix})`
}

/**
 * 构建知识下拉选项展示文案
 */
export function formatKnowledgeOptionLabel(knowledge) {
    if (!knowledge) {
        return ''
    }
    const title = knowledge.title || '未命名知识'
    const typeLabel = KNOWLEDGE_TYPE_LABELS[knowledge.knowledgeType] || knowledge.knowledgeType || ''
    const typeSuffix = typeLabel ? ` · ${typeLabel}` : ''
    return `${title} (#${knowledge.id}${typeSuffix})`
}

/**
 * 构建闭环追踪页路径
 */
export function buildMemoryTracePath(options = {}) {
    const query = {}
    if (options.draftId) {
        query.draftId = String(options.draftId)
    }
    if (options.knowledgeId) {
        query.knowledgeId = String(options.knowledgeId)
    }
    if (!query.draftId && !query.knowledgeId) {
        return null
    }
    const searchParams = new URLSearchParams(query)
    return `/trace?${searchParams.toString()}`
}

/**
 * 根据知识类型与生命周期构建详情页路径
 */
export function buildKnowledgeDetailPath(knowledgeItem) {
    if (!knowledgeItem?.knowledgeId) {
        return null
    }
    const knowledgeId = knowledgeItem.knowledgeId
    const knowledgeType = knowledgeItem.knowledgeType
    const lifecycleStatus = knowledgeItem.lifecycleStatus

    if (knowledgeType === 'experience') {
        return `/experience/${knowledgeId}`
    }
    if (knowledgeType === 'rule' || knowledgeType === 'workflow') {
        if (lifecycleStatus === KNOWLEDGE_LIFECYCLE.DRAFT) {
            const typeQuery = knowledgeType === 'workflow' ? '?type=workflow' : ''
            return `/rule/drafts/${knowledgeId}${typeQuery}`
        }
        return `/rule/${knowledgeId}`
    }
    if (knowledgeType === 'decision') {
        if (lifecycleStatus === KNOWLEDGE_LIFECYCLE.DRAFT) {
            return `/decision/drafts/${knowledgeId}`
        }
        return `/decision/${knowledgeId}`
    }
    return `/experience/${knowledgeId}`
}

/**
 * Capture Review 动作中文标签
 */
export function formatReviewAction(reviewAction) {
    if (!reviewAction) {
        return '—'
    }
    return CAPTURE_REVIEW_ACTION_LABELS[reviewAction] || reviewAction
}

/**
 * Capture 审核状态中文标签
 */
export function formatReviewStatus(reviewStatus) {
    if (reviewStatus === undefined || reviewStatus === null) {
        return '—'
    }
    return CAPTURE_REVIEW_STATUS_LABELS[reviewStatus] || String(reviewStatus)
}

/**
 * 判断 Recall 是否 Top1 命中（用于验收提示）
 */
export function hasTopOneRecallHit(recallHitList) {
    if (!recallHitList?.length) {
        return false
    }
    return recallHitList.some((hitItem) => hitItem.rank === 1)
}

/**
 * 按 Recall 时间倒序排列，最新在前
 */
export function sortRecallHitsByTime(recallHitList) {
    if (!recallHitList?.length) {
        return []
    }
    return [...recallHitList].sort((left, right) => {
        const leftTime = left.createTime ? new Date(left.createTime).getTime() : 0
        const rightTime = right.createTime ? new Date(right.createTime).getTime() : 0
        return rightTime - leftTime
    })
}

/**
 * 闭环追踪入口类型中文标签
 */
export function formatTraceEntryLabel(entryType, entryId) {
    if (entryType === 'draft') {
        return `草稿 #${entryId}`
    }
    if (entryType === 'knowledge') {
        return `知识 #${entryId}`
    }
    if (entryType === 'recall') {
        return `最近一次召回 #${entryId}`
    }
    return entryId ? `#${entryId}` : '—'
}

/**
 * 格式化 Recall 分数，保留三位小数
 */
export function formatRecallScore(scoreValue) {
    if (scoreValue == null) {
        return '—'
    }
    return Number(scoreValue).toFixed(3)
}

/**
 * 将 scoreBreakdown 转为可展示的键值列表
 */
export function formatScoreBreakdownEntries(scoreBreakdown) {
    if (!scoreBreakdown || typeof scoreBreakdown !== 'object') {
        return []
    }
    return Object.entries(scoreBreakdown).map(([key, value]) => ({
        key,
        label: RECALL_SCORE_BREAKDOWN_LABELS[key] || key,
        value: formatRecallScore(value),
    }))
}
