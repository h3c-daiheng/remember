/**
 * 工作空间数据统计 API
 * 对应后端 WorkspaceStatsController /workspace/{workspaceId}/stats/*
 */
import { apiRequest } from '~/services/http'

/**
 * 查询统计概览
 * @param {number|string} workspaceId 工作空间 ID
 * @param {number} [days=7] 统计窗口天数
 */
export function fetchStatsOverview(workspaceId, days = 7) {
    const query = new URLSearchParams({ days: String(days) })
    return apiRequest(`/workspace/${workspaceId}/stats/overview?${query.toString()}`)
}

/**
 * 查询 Recall / Remember / Feedback 日趋势
 * @param {number|string} workspaceId 工作空间 ID
 * @param {number} [days=30] 趋势天数
 */
export function fetchStatsRecallTrend(workspaceId, days = 30) {
    const query = new URLSearchParams({ days: String(days) })
    return apiRequest(`/workspace/${workspaceId}/stats/recall-trend?${query.toString()}`)
}

/**
 * 查询被召回次数 Top N 经验
 * @param {number|string} workspaceId 工作空间 ID
 * @param {number} [limit=10] 返回条数
 */
export function fetchStatsTopKnowledge(workspaceId, limit = 10) {
    const query = new URLSearchParams({ limit: String(limit) })
    return apiRequest(`/workspace/${workspaceId}/stats/top-knowledge?${query.toString()}`)
}

/**
 * 查询月用量统计（usage_daily 日聚合）
 * @param {number|string} workspaceId 工作空间 ID
 * @param {string} [month] 统计月份 yyyy-MM，默认当前月
 */
export function fetchStatsUsage(workspaceId, month) {
    const query = new URLSearchParams()
    if (month) {
        query.set('month', month)
    }
    const queryString = query.toString()
    const suffix = queryString ? `?${queryString}` : ''
    return apiRequest(`/workspace/${workspaceId}/stats/usage${suffix}`)
}

/**
 * 查询 Capture 闭环漏斗
 */
export function fetchStatsFunnel(workspaceId, days = 30) {
    const query = new URLSearchParams({ days: String(days) })
    return apiRequest(`/workspace/${workspaceId}/stats/funnel?${query.toString()}`)
}

/**
 * 查询多维分析数据
 */
export function fetchStatsDimensions(workspaceId, options = {}) {
    const query = new URLSearchParams({ days: String(options.days || 30) })
    if (options.module) {
        query.set('module', options.module)
    }
    if (options.project) {
        query.set('project', options.project)
    }
    if (options.tag) {
        query.set('tag', options.tag)
    }
    return apiRequest(`/workspace/${workspaceId}/stats/dimensions?${query.toString()}`)
}

/**
 * 查询图谱枢纽经验 Top N
 */
export function fetchStatsGraphHubs(workspaceId, limit = 10) {
    const query = new URLSearchParams({ limit: String(limit) })
    return apiRequest(`/workspace/${workspaceId}/stats/graph-hubs?${query.toString()}`)
}
