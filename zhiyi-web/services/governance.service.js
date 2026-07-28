/**
 * 记忆治理 API：重复扫描、碎片聚类、合并优化与工单处置
 */
import { apiRequest } from '~/services/http'

/**
 * 触发治理扫描
 * @param {{ scanType?: string, scanMode?: string, knowledgeType?: string, moduleFilter?: string, similarityThreshold?: number }} [scanRequest]
 */
export function runGovernanceScan(scanRequest = {}) {
    return apiRequest('/governance/scans', {
        method: 'POST',
        body: JSON.stringify(scanRequest),
    })
}

/**
 * 分页查询治理工单
 */
export function fetchGovernanceIssueList(params = {}) {
    const query = new URLSearchParams()
    query.set('pageNum', String(params.pageNum || 1))
    query.set('pageSize', String(params.pageSize || 20))
    if (params.status !== undefined && params.status !== null && params.status !== '') {
        query.set('status', String(params.status))
    }
    if (params.issueType) {
        query.set('issueType', String(params.issueType).trim())
    }
    return apiRequest(`/governance/issues?${query.toString()}`)
}

/**
 * 查询治理工单详情
 */
export function fetchGovernanceIssueDetail(issueId) {
    return apiRequest(`/governance/issues/${issueId}`)
}

/**
 * 处置治理工单
 */
export function resolveGovernanceIssue(issueId, resolveRequest) {
    return apiRequest(`/governance/issues/${issueId}/resolve`, {
        method: 'POST',
        body: JSON.stringify(resolveRequest),
    })
}

/**
 * 治理统计摘要
 */
export function fetchGovernanceStats() {
    return apiRequest('/governance/stats')
}

/**
 * 查询工作空间治理配置
 */
export function fetchGovernanceConfig() {
    return apiRequest('/governance/config')
}

/**
 * 更新工作空间治理配置
 */
export function updateGovernanceConfig(configRequest) {
    return apiRequest('/governance/config', {
        method: 'POST',
        body: JSON.stringify(configRequest),
    })
}

/**
 * 生成合并预览
 */
export function previewGovernanceMerge(previewRequest) {
    return apiRequest('/governance/merge/preview', {
        method: 'POST',
        body: JSON.stringify(previewRequest),
    })
}

/**
 * 工单级合并预览
 */
export function previewGovernanceMergeByIssue(issueId, previewRequest = {}) {
    return apiRequest(`/governance/issues/${issueId}/preview-merge`, {
        method: 'POST',
        body: JSON.stringify(previewRequest),
    })
}

/**
 * 确认合并优化
 */
export function confirmGovernanceMerge(confirmRequest) {
    return apiRequest('/governance/merge/confirm', {
        method: 'POST',
        body: JSON.stringify(confirmRequest),
    })
}
