/**
 * Capture 领域 API：Agent Remember 产生的草稿 Review 闭环
 * 对应后端 CaptureController /capture/*（前端 apiBase 加 /api 前缀）
 */
import { apiRequest } from '~/services/http'

/**
 * 查询待确认 Capture 草稿列表
 * @param {{ aiReviewDecision?: string }} [options] 可按 AI Review 决策筛选
 */
export function fetchCaptureDraftList(options = {}) {
    const query = new URLSearchParams()
    const decision = String(options.aiReviewDecision || '').trim()
    if (decision) {
        query.set('aiReviewDecision', decision)
    }
    const queryText = query.toString()
    return apiRequest(queryText ? `/capture/drafts?${queryText}` : '/capture/drafts')
}

/**
 * 分页查询 AI Review 记录列表（全量历史）
 * @param {{ pageNum?: number, pageSize?: number, status?: number|string, decision?: string, similarHit?: boolean|string, keyword?: string }} [params]
 */
export function fetchAiReviewList(params = {}) {
    const query = new URLSearchParams()
    query.set('pageNum', String(params.pageNum || 1))
    query.set('pageSize', String(params.pageSize || 20))
    if (params.status !== undefined && params.status !== null && params.status !== '') {
        query.set('status', String(params.status))
    }
    if (params.decision) {
        query.set('decision', String(params.decision).trim())
    }
    if (params.similarHit !== undefined && params.similarHit !== null && params.similarHit !== '') {
        query.set('similarHit', String(params.similarHit))
    }
    const keyword = String(params.keyword || '').trim()
    if (keyword) {
        query.set('keyword', keyword)
    }
    return apiRequest(`/capture/ai-reviews?${query.toString()}`)
}

/**
 * 按记录主键查询 AI Review 详情
 */
export function fetchAiReviewById(reviewId) {
    return apiRequest(`/capture/ai-reviews/${reviewId}`)
}

/**
 * 查询草稿最新 AI Review 记录
 */
export function fetchCaptureAiReview(draftId) {
    return apiRequest(`/capture/drafts/${draftId}/ai-review`)
}

/**
 * 重跑 AI Review（仅待确认草稿）
 */
export function retryCaptureAiReview(draftId) {
    return apiRequest(`/capture/drafts/${draftId}/ai-review/retry`, {
        method: 'POST',
    })
}

/**
 * 按关键词搜索 Capture 草稿（含各审核状态），供闭环追踪选用
 */
export function searchCaptureDrafts(keyword = '', limit = 20) {
    const query = new URLSearchParams({
        limit: String(limit),
    })
    const trimmedKeyword = String(keyword || '').trim()
    if (trimmedKeyword) {
        query.set('keyword', trimmedKeyword)
    }
    return apiRequest(`/capture/drafts/search?${query.toString()}`)
}

/**
 * 查询 Capture 草稿详情
 */
export function fetchCaptureDraftDetail(draftId) {
    return apiRequest(`/capture/drafts/${draftId}`)
}

/**
 * 采纳草稿：Review 通过 → 写入 knowledge + facts + artifacts → 直接发布
 */
export function approveCaptureDraft(draftId, draftContent) {
    return apiRequest(`/capture/drafts/${draftId}/approve`, {
        method: 'POST',
        body: JSON.stringify(draftContent || {}),
    })
}

/**
 * 拒绝草稿，可选携带拒绝码与备注
 */
export function rejectCaptureDraft(draftId, rejectRequest) {
    return apiRequest(`/capture/drafts/${draftId}/reject`, {
        method: 'POST',
        body: JSON.stringify(rejectRequest || {}),
    })
}

/**
 * 路由草稿为 Rule / Workflow / Decision 并直接发布
 */
export function routeCaptureDraft(draftId, routeRequest) {
    return apiRequest(`/capture/drafts/${draftId}/route`, {
        method: 'POST',
        body: JSON.stringify(routeRequest),
    })
}

/**
 * 合并草稿 Fact 到已有 Rule，并关闭草稿
 */
export function mergeCaptureDraftIntoRule(draftId, mergeRequest) {
    return apiRequest(`/capture/drafts/${draftId}/merge-into-rule`, {
        method: 'POST',
        body: JSON.stringify(mergeRequest),
    })
}
