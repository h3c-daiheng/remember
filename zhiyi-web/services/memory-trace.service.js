/**
 * Memory 闭环追踪 API
 * 对应后端 MemoryDebugController GET /memory/debug/trace
 */
import { apiRequest } from '~/services/http'

/**
 * 按 draftId 或 knowledgeId 查询闭环追踪视图
 * @param {{ draftId?: number|string, knowledgeId?: number|string }} query
 */
export function fetchMemoryTrace(query = {}) {
    const searchParams = new URLSearchParams()
    if (query.draftId) {
        searchParams.set('draftId', String(query.draftId))
    }
    if (query.knowledgeId) {
        searchParams.set('knowledgeId', String(query.knowledgeId))
    }
    const queryString = searchParams.toString()
    return apiRequest(`/memory/debug/trace${queryString ? `?${queryString}` : ''}`)
}

/**
 * 分页查询工作空间内全部 Agent Recall / Search 请求记录
 * @param {{ pageNum?: number, pageSize?: number, operation?: string, success?: number|string, keyword?: string }} [params]
 */
export function fetchRecallLogList(params = {}) {
    const searchParams = new URLSearchParams()
    searchParams.set('pageNum', String(params.pageNum || 1))
    searchParams.set('pageSize', String(params.pageSize || 20))
    if (params.operation) {
        searchParams.set('operation', String(params.operation))
    }
    if (params.success !== undefined && params.success !== null && params.success !== '') {
        searchParams.set('success', String(params.success))
    }
    if (params.keyword) {
        searchParams.set('keyword', String(params.keyword).trim())
    }
    return apiRequest(`/memory/debug/recall-logs?${searchParams.toString()}`)
}

/**
 * 查询单次 Recall 操作详情，含输入、Query、排序分解与 promptBlock 预览
 * @param {number|string} logId memory_operation_log.id
 */
export function fetchRecallLogDetail(logId) {
    const searchParams = new URLSearchParams()
    searchParams.set('logId', String(logId))
    return apiRequest(`/memory/debug/recall-log?${searchParams.toString()}`)
}
