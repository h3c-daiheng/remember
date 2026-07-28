/**
 * 经验搜索、详情、关系与图谱 API
 * 对应后端 KnowledgeController /knowledge/*、GraphController /graph/*
 */
import { apiRequest } from '~/services/http'
import { getStoredToken } from '~/utils/token'
import { appendTraceSearchFilters } from '~/utils/traceSearch'

/**
 * 分页查询知识列表，支持按类型与生命周期筛选
 */
export function fetchKnowledgeList(pageNum = 1, pageSize = 20, keyword = '', options = {}) {
    const query = new URLSearchParams({
        pageNum: String(pageNum),
        pageSize: String(pageSize),
    })
    if (keyword) {
        query.set('keyword', keyword)
    }
    if (options.knowledgeType) {
        query.set('knowledgeType', options.knowledgeType)
    }
    if (options.lifecycleStatus !== undefined && options.lifecycleStatus !== null) {
        query.set('lifecycleStatus', String(options.lifecycleStatus))
    }
    return apiRequest(`/knowledge/list?${query.toString()}`)
}

/**
 * 跨类型搜索知识，供闭环追踪等下拉选择场景
 * @param {{ title?: string, project?: string, module?: string, repository?: string, tag?: string }} filters
 */
export function searchKnowledgeForSelector(filters = {}, pageSize = 20) {
    const query = new URLSearchParams({
        pageNum: '1',
        pageSize: String(pageSize),
    })
    appendTraceSearchFilters(query, filters)
    return apiRequest(`/knowledge/selector?${query.toString()}`)
}

/**
 * 查询经验详情（含 Fact Blocks 与 Artifacts）
 */
export function fetchKnowledgeDetail(knowledgeId) {
    return apiRequest(`/knowledge/${knowledgeId}`)
}

/**
 * 人工创建知识（经验 / 规则 / 流程 / 决策），可指定 knowledgeType 与 publish
 */
export function createKnowledge(saveRequest) {
    return apiRequest('/knowledge', {
        method: 'POST',
        body: JSON.stringify(saveRequest),
    })
}

/**
 * 更新经验
 */
export function updateKnowledge(knowledgeId, saveRequest) {
    return apiRequest(`/knowledge/${knowledgeId}`, {
        method: 'PUT',
        body: JSON.stringify(saveRequest),
    })
}

/**
 * 发布经验（lifecycle_status → 已发布，并触发向量索引）
 */
export function publishKnowledge(knowledgeId) {
    return apiRequest(`/knowledge/${knowledgeId}/publish`, {
        method: 'POST',
    })
}

/**
 * 下架已发布经验：标记为已失效，Recall 不再返回
 */
export function deprecateKnowledge(knowledgeId) {
    return apiRequest(`/knowledge/${knowledgeId}/deprecate`, {
        method: 'POST',
    })
}

/**
 * 重新启用已失效经验：恢复为已发布并重建 Recall 索引
 */
export function reactivateKnowledge(knowledgeId) {
    return apiRequest(`/knowledge/${knowledgeId}/reactivate`, {
        method: 'POST',
    })
}

/**
 * 删除经验（逻辑删除）
 */
export function deleteKnowledge(knowledgeId) {
    return apiRequest(`/knowledge/${knowledgeId}`, {
        method: 'DELETE',
    })
}

/**
 * 查询单条经验的 Feedback 统计摘要
 */
export function fetchKnowledgeFeedbackSummary(knowledgeId) {
    return apiRequest(`/knowledge/${knowledgeId}/feedback-summary`)
}

/**
 * 查询与指定经验相关的已发布经验列表
 */
export function fetchKnowledgeRelated(knowledgeId, limit = 10) {
    const query = new URLSearchParams({
        limit: String(limit),
    })
    return apiRequest(`/knowledge/${knowledgeId}/related?${query.toString()}`)
}

/**
 * 查询与指定知识关联的决策关系边（experience ↔ decision）
 */
export function fetchKnowledgeRelatedDecisions(knowledgeId) {
    return fetchKnowledgeRelations(knowledgeId, { types: 'related_decision' })
}

/**
 * 查询指定经验的显式关系边
 */
export function fetchKnowledgeRelations(knowledgeId, options = {}) {
    const query = new URLSearchParams()
    if (options.types) {
        query.set('types', options.types)
    }
    const queryString = query.toString()
    const suffix = queryString ? `?${queryString}` : ''
    return apiRequest(`/knowledge/${knowledgeId}/relations${suffix}`)
}

/**
 * 人工创建关系边
 */
export function createKnowledgeRelation(sourceId, payload) {
    return apiRequest(`/knowledge/${sourceId}/relations`, {
        method: 'POST',
        body: JSON.stringify(payload),
    })
}

/**
 * 删除关系边
 */
export function deleteKnowledgeRelation(relationId) {
    return apiRequest(`/knowledge/relations/${relationId}`, {
        method: 'DELETE',
    })
}

/**
 * 查询以 centerId 为中心的图谱子图
 */
export function fetchKnowledgeGraph(centerId, options = {}) {
    const query = new URLSearchParams({
        centerId: String(centerId),
        depth: String(options.depth ?? 1),
        limit: String(options.limit ?? 50),
    })
    if (options.types) {
        query.set('types', options.types)
    }
    return apiRequest(`/graph?${query.toString()}`)
}

/**
 * 查询指定经验的版本时间线
 */
export function fetchKnowledgeTimeline(knowledgeId) {
    return apiRequest(`/knowledge/${knowledgeId}/timeline`)
}

/**
 * 新版经验替代旧版
 */
export function supersedeKnowledge(successorId, payload) {
    return apiRequest(`/knowledge/${successorId}/supersede`, {
        method: 'POST',
        body: JSON.stringify(payload),
    })
}

/**
 * 导出当前工作空间知识为 Markdown（后端返回 text/markdown 文件流，绕过 apiRequest 的 JSON 解包）
 */
export async function exportKnowledgeMarkdown(knowledgeType = 'all') {
  const runtimeConfig = useRuntimeConfig()
  const token = getStoredToken()
  const query = knowledgeType ? `?knowledgeType=${encodeURIComponent(knowledgeType)}` : ''
  const response = await fetch(`${runtimeConfig.public.apiBase}/knowledge/export${query}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!response.ok) {
    throw new Error('导出失败')
  }
  return response.text()
}
