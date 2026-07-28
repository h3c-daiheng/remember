/**
 * 经验搜索页常量：默认筛选与表单选项
 */
import { FACT_BLOCK_TYPES, KNOWLEDGE_TYPES } from '~/constants/knowledge'

/**
 * Beta Recall 默认注入的 Fact 类型
 * 与后端 RecallEngine.filterFacts（传 knowledgeTypes 时）保持一致
 */
export const RECALL_DEFAULT_FACT_TYPES = ['rule', 'constraint', 'decision', 'action']

/** Web 搜索默认召回的知识类型（Beta：Rule 优先，含 Decision） */
export const SEARCH_DEFAULT_KNOWLEDGE_TYPES = [
    KNOWLEDGE_TYPES.RULE,
    KNOWLEDGE_TYPES.WORKFLOW,
    KNOWLEDGE_TYPES.DECISION,
    KNOWLEDGE_TYPES.EXPERIENCE,
]

/** 知识类型筛选项 */
export const SEARCH_KNOWLEDGE_TYPE_OPTIONS = [
    { value: KNOWLEDGE_TYPES.RULE, label: 'Rule 规范' },
    { value: KNOWLEDGE_TYPES.WORKFLOW, label: 'Workflow 流程' },
    { value: KNOWLEDGE_TYPES.DECISION, label: 'Decision 决策' },
    { value: KNOWLEDGE_TYPES.EXPERIENCE, label: 'Experience 经验' },
]

/** Fact 类型筛选项（可选，不传则返回全部 Fact） */
export const SEARCH_FACT_TYPE_OPTIONS = FACT_BLOCK_TYPES.map((factType) => ({
    value: factType,
    label: factType,
}))

/** 默认返回条数 */
export const SEARCH_DEFAULT_LIMIT = 10

/** 可选返回条数 */
export const SEARCH_LIMIT_OPTIONS = [5, 10, 15, 20]
