/**
 * Agent 注入片段预览：与 RecallEngine 单条 RecallItem 的 prompt 格式对齐
 * 仅模拟本条 Knowledge 的贡献，不含多条目合并后的完整 promptBlock
 */
import { FACT_TYPE_LABELS, KNOWLEDGE_TYPES } from '~/constants/knowledge'
import { RECALL_DEFAULT_FACT_TYPES } from '~/constants/memorySearch'

/**
 * 按 Recall 默认 factTypes 过滤 Fact，逻辑与后端 RecallEngine.filterFacts 一致
 * @param {Array<{ type: string, text: string }>} facts
 * @param {string[]} targetFactTypes
 * @returns {{ injectedFacts: Array, excludedFacts: Array, usedFallback: boolean }}
 */
export function filterFactsForRecallPreview(facts, targetFactTypes = RECALL_DEFAULT_FACT_TYPES) {
    if (!facts || facts.length === 0) {
        return {
            injectedFacts: [],
            excludedFacts: [],
            usedFallback: false,
        }
    }

    const injectedFacts = facts.filter((factItem) => targetFactTypes.includes(factItem.type))
    if (injectedFacts.length === 0) {
        return {
            injectedFacts: facts,
            excludedFacts: [],
            usedFallback: true,
        }
    }

    const excludedFacts = facts.filter((factItem) => !targetFactTypes.includes(factItem.type))
    return {
        injectedFacts,
        excludedFacts,
        usedFallback: false,
    }
}

/**
 * 根据知识类型解析 promptBlock 中的分组标题
 * @param {string} knowledgeType
 * @returns {string}
 */
export function resolvePromptSectionTitle(knowledgeType) {
    if (knowledgeType === KNOWLEDGE_TYPES.RULE) {
        return '规则'
    }
    if (knowledgeType === KNOWLEDGE_TYPES.WORKFLOW) {
        return '流程'
    }
    return '经验'
}

/**
 * 组装单条 RecallItem 的 prompt 片段（不含 ## 分组标题）
 * @param {string} title
 * @param {Array<{ type: string, text: string }>} factList
 * @returns {string}
 */
export function buildRecallItemPromptSlice(title, factList) {
    if (!factList || factList.length === 0) {
        return ''
    }

    const lineList = [`### ${title || '未命名'}`]
    for (const factItem of factList) {
        lineList.push(`- [${factItem.type}] ${factItem.text}`)
    }
    return `${lineList.join('\n')}\n`
}

/**
 * 构建本条 Knowledge 在 Recall promptBlock 中的注入片段预览
 * @param {{ title?: string, knowledgeType?: string, facts?: Array }} knowledge
 * @returns {{
 *   promptSlice: string,
 *   injectedFacts: Array,
 *   excludedFacts: Array,
 *   usedFallback: boolean,
 *   excludedTypeLabels: string[],
 * }}
 */
export function buildKnowledgePromptSlicePreview(knowledge) {
    if (!knowledge) {
        return {
            promptSlice: '',
            injectedFacts: [],
            excludedFacts: [],
            usedFallback: false,
            excludedTypeLabels: [],
        }
    }

    const filterResult = filterFactsForRecallPreview(knowledge.facts)
    const itemSlice = buildRecallItemPromptSlice(knowledge.title, filterResult.injectedFacts)
    if (!itemSlice) {
        return {
            promptSlice: '',
            injectedFacts: [],
            excludedFacts: filterResult.excludedFacts,
            usedFallback: filterResult.usedFallback,
            excludedTypeLabels: buildExcludedTypeLabels(filterResult.excludedFacts),
        }
    }

    const sectionTitle = resolvePromptSectionTitle(knowledge.knowledgeType)
    return {
        promptSlice: `## ${sectionTitle}\n${itemSlice}`.trim(),
        injectedFacts: filterResult.injectedFacts,
        excludedFacts: filterResult.excludedFacts,
        usedFallback: filterResult.usedFallback,
        excludedTypeLabels: buildExcludedTypeLabels(filterResult.excludedFacts),
    }
}

/**
 * 汇总未注入 Fact 的类型标签，便于详情页提示作者
 * @param {Array<{ type: string }>} excludedFacts
 * @returns {string[]}
 */
function buildExcludedTypeLabels(excludedFacts) {
    if (!excludedFacts || excludedFacts.length === 0) {
        return []
    }
    const typeSet = new Set(excludedFacts.map((factItem) => factItem.type))
    return Array.from(typeSet).map((factType) => FACT_TYPE_LABELS[factType] || factType)
}
