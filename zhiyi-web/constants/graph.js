/**
 * 经验图谱常量：关系类型标签、颜色与节点类型配色
 * 与后端 RelationConstants.relation_type 对齐
 */

/** 关系类型中文标签 */
export const RELATION_TYPE_LABELS = {
    same_module: '同模块',
    same_tag: '同标签',
    related_semantic: '语义相近',
    same_artifact: '同源 Artifact',
    related_decision: '关联决策',
    references: '显式引用',
    depends_on: '依赖',
    supersedes: '替代演进',
}

/** 关系类型筛选选项 */
export const RELATION_TYPE_OPTIONS = Object.entries(RELATION_TYPE_LABELS).map(([value, label]) => ({
    value,
    label,
}))

/** 关系边在力导向图中的线型 */
export const RELATION_TYPE_LINE_STYLE = {
    same_module: 'solid',
    same_tag: 'dotted',
    related_semantic: 'solid',
    same_artifact: 'dashed',
    related_decision: 'solid',
    references: 'dashed',
    depends_on: 'solid',
    supersedes: 'dashed',
}

/** 关系边颜色 */
export const RELATION_TYPE_COLORS = {
    same_module: '#64748b',
    same_tag: '#0ea5e9',
    related_semantic: '#8b5cf6',
    same_artifact: '#14b8a6',
    related_decision: '#f59e0b',
    references: '#6366f1',
    depends_on: '#ef4444',
    supersedes: '#94a3b8',
}

/** 知识类型节点颜色 */
export const GRAPH_NODE_COLORS = {
    experience: '#2563eb',
    rule: '#ea580c',
    workflow: '#059669',
    decision: '#9333ea',
}

/** 默认子图深度 */
export const GRAPH_DEFAULT_DEPTH = 2

/** 默认边数上限 */
export const GRAPH_DEFAULT_LIMIT = 50

/** 获取关系类型标签 */
export function getRelationTypeLabel(relationType) {
    return RELATION_TYPE_LABELS[relationType] || relationType
}

/** 获取知识类型节点颜色 */
export function getGraphNodeColor(knowledgeType) {
    return GRAPH_NODE_COLORS[knowledgeType] || '#64748b'
}

/** 获取关系边颜色 */
export function getRelationEdgeColor(relationType) {
    return RELATION_TYPE_COLORS[relationType] || '#cbd5e1'
}

/** 根据 recallCount 计算节点大小 */
export function resolveGraphNodeSize(recallCount, inboundDegree = 0) {
    const recallWeight = Math.min(recallCount || 0, 50) / 50
    const degreeWeight = Math.min(inboundDegree, 10) / 10
    return 18 + recallWeight * 14 + degreeWeight * 8
}

/** 根据 knowledgeType 返回详情页路径前缀 */
export function resolveKnowledgeDetailPath(knowledgeType) {
    if (knowledgeType === 'rule') return '/rule'
    if (knowledgeType === 'decision') return '/decision'
    return '/experience'
}
