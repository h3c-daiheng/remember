/**
 * Knowledge 领域纯函数：Fact 分组、数据格式化等，无网络依赖
 */
import {
    ARTIFACT_ROLE_LABELS,
    DEFAULT_FACT_TYPES_BY_KNOWLEDGE_TYPE,
    FACT_BLOCK_TYPES,
    FACT_TYPE_LABELS,
    KNOWLEDGE_LIFECYCLE_LABELS,
    KNOWLEDGE_TYPE_LABELS,
    KNOWLEDGE_TYPES,
} from '~/constants/knowledge'

/**
 * 按 Fact Block 类型分组，保持 observation → outcome 的展示顺序
 * @param {Array<{ type: string, text: string, metadata?: object }>} facts
 * @returns {Array<{ type: string, items: Array }>}
 */
export function groupFactsByType(facts) {
    if (!facts || facts.length === 0) {
        return []
    }

    const groupMap = {}
    for (const fact of facts) {
        if (!groupMap[fact.type]) {
            groupMap[fact.type] = { type: fact.type, items: [] }
        }
        groupMap[fact.type].items.push(fact)
    }

    return FACT_BLOCK_TYPES
        .filter((blockType) => groupMap[blockType])
        .map((blockType) => groupMap[blockType])
}

/**
 * 格式化后端时间字段为可读字符串
 */
export function formatDateTime(timeValue) {
    if (!timeValue) {
        return ''
    }
    return String(timeValue).replace('T', ' ').slice(0, 16)
}

/**
 * 解析知识提交人展示昵称
 */
export function resolveKnowledgeSubmitterName(knowledge) {
    if (!knowledge) {
        return ''
    }
    return String(knowledge.creatorNickname || knowledge.submitterNickname || '').trim()
}

/**
 * 构建提交人头像组件所需的用户对象
 */
export function buildKnowledgeSubmitterUser(knowledge) {
    if (!knowledge) {
        return null
    }
    const nickname = resolveKnowledgeSubmitterName(knowledge)
    const avatar = knowledge.creatorAvatar || knowledge.submitterAvatar || ''
    if (!nickname && !avatar) {
        return null
    }
    return {
        nickname,
        avatar,
    }
}

/**
 * 将 Capture 草稿转为 KnowledgeSubmitter 可识别的对象
 */
export function buildCaptureDraftSubmitterKnowledge(draft) {
    if (!draft) {
        return null
    }
    return {
        submitterNickname: draft.submitterNickname,
        submitterAvatar: draft.submitterAvatar,
    }
}

/**
 * 构建 Knowledge 元信息摘要（项目 / 模块 / 仓库）
 */
export function buildKnowledgeMetaSummary(knowledge) {
    if (!knowledge) {
        return []
    }
    const metaItems = []
    if (knowledge.project) {
        metaItems.push({ label: '项目', value: knowledge.project })
    }
    if (knowledge.module) {
        metaItems.push({ label: '模块', value: knowledge.module })
    }
    if (knowledge.repository) {
        metaItems.push({ label: '仓库', value: knowledge.repository })
    }
    if (knowledge.language) {
        metaItems.push({ label: '语言', value: knowledge.language })
    }
    if (knowledge.framework) {
        metaItems.push({ label: '框架', value: knowledge.framework })
    }
    return metaItems
}

/**
 * 提取 Capture 草稿中可合并到 Rule 的 rule/constraint Fact，与后端 FactTransformEngine 对齐
 */
export function extractRuleFactsFromDraft(draftContent) {
    if (!draftContent?.facts?.length) {
        return []
    }
    return draftContent.facts.filter(
        (fact) => fact?.text && (fact.type === 'rule' || fact.type === 'constraint'),
    )
}

/**
 * 深拷贝经验对象，供编辑抽屉使用，避免直接修改详情页展示数据
 */
export function cloneKnowledgeForEdit(knowledge) {
    if (!knowledge) {
        return null
    }
    return JSON.parse(JSON.stringify(knowledge))
}

/**
 * 将经验详情转换为后端 KnowledgeSaveRequest 结构
 */
export function buildKnowledgeSaveRequest(knowledge) {
    if (!knowledge) {
        return {}
    }
    return {
        title: knowledge.title || '',
        project: knowledge.project || null,
        module: knowledge.module || null,
        repository: knowledge.repository || null,
        language: knowledge.language || null,
        framework: knowledge.framework || null,
        tags: knowledge.tags || [],
        knowledgeType: knowledge.knowledgeType || null,
        publish: knowledge.publish === true,
        facts: (knowledge.facts || []).map((fact) => ({
            type: fact.type,
            text: fact.text,
            metadata: fact.metadata,
        })),
        artifacts: (knowledge.artifacts || []).map((artifact) => ({
            artifactType: artifact.artifactType,
            artifactRole: artifact.artifactRole,
            artifactUrl: artifact.artifactUrl,
            contentRef: artifact.contentRef,
        })),
    }
}

/**
 * 构建空白知识模板，供各中心「新建」直达草稿编辑页
 * @param {string} knowledgeType experience / rule / workflow / decision
 */
export function buildEmptyKnowledgeTemplate(knowledgeType) {
    const resolvedType = knowledgeType || KNOWLEDGE_TYPES.EXPERIENCE
    const defaultFactTypes = DEFAULT_FACT_TYPES_BY_KNOWLEDGE_TYPE[resolvedType]
        || DEFAULT_FACT_TYPES_BY_KNOWLEDGE_TYPE.experience
    const defaultTitles = {
        [KNOWLEDGE_TYPES.EXPERIENCE]: '未命名经验',
        [KNOWLEDGE_TYPES.RULE]: '未命名规则',
        [KNOWLEDGE_TYPES.WORKFLOW]: '未命名流程',
        [KNOWLEDGE_TYPES.DECISION]: '未命名决策',
    }
    return {
        title: defaultTitles[resolvedType] || '未命名知识',
        knowledgeType: resolvedType,
        project: null,
        module: null,
        repository: null,
        language: null,
        framework: null,
        tags: [],
        facts: defaultFactTypes.map((factType) => ({
            type: factType,
            text: '',
        })),
        artifacts: [],
    }
}

/**
 * 解析知识草稿编辑页路由
 */
export function resolveKnowledgeDraftPath(knowledgeType, knowledgeId) {
    switch (knowledgeType) {
        case KNOWLEDGE_TYPES.RULE:
        case KNOWLEDGE_TYPES.WORKFLOW:
            return `/rule/drafts/${knowledgeId}`
        case KNOWLEDGE_TYPES.DECISION:
            return `/decision/drafts/${knowledgeId}`
        case KNOWLEDGE_TYPES.EXPERIENCE:
        default:
            return `/experience/drafts/${knowledgeId}`
    }
}

/**
 * 校验草稿是否可发布，返回错误文案；通过时返回 null
 */
export function validateKnowledgeDraftForPublish(knowledge) {
    if (!knowledge?.title?.trim()) {
        return '请填写标题'
    }
    const validFacts = (knowledge.facts || []).filter((fact) => fact?.text?.trim())
    if (validFacts.length === 0) {
        return '请至少填写一条有效内容'
    }
    return null
}

/**
 * 将知识详情序列化为 Markdown，便于复制分享或粘贴到其他文档工具
 * @param {object} knowledge 知识详情对象
 * @param {{ detailUrl?: string }} options detailUrl 为详情页完整 URL，写入文末来源链接
 * @returns {string}
 */
export function buildKnowledgeDetailMarkdown(knowledge, options = {}) {
    if (!knowledge) {
        return ''
    }

    const lines = []
    const title = knowledge.title || '未命名'
    lines.push(`# ${title}`)
    lines.push('')

    const summaryParts = []
    const typeLabel = KNOWLEDGE_TYPE_LABELS[knowledge.knowledgeType] || knowledge.knowledgeType || '知识'
    summaryParts.push(typeLabel)
    if (knowledge.lifecycleStatus !== undefined && KNOWLEDGE_LIFECYCLE_LABELS[knowledge.lifecycleStatus]) {
        summaryParts.push(KNOWLEDGE_LIFECYCLE_LABELS[knowledge.lifecycleStatus])
    }
    if (typeof knowledge.recallCount === 'number') {
        summaryParts.push(`召回 ${knowledge.recallCount} 次`)
    }
    const updateTime = formatDateTime(knowledge.updateTime)
    if (updateTime) {
        summaryParts.push(`更新于 ${updateTime}`)
    }
    if (summaryParts.length > 0) {
        lines.push(`> ${summaryParts.join(' · ')}`)
        lines.push('')
    }

    const metaItems = buildKnowledgeMetaSummary(knowledge)
    if (knowledge.tags?.length) {
        metaItems.push({ label: '标签', value: knowledge.tags.join('、') })
    }
    if (metaItems.length > 0) {
        for (const metaItem of metaItems) {
            lines.push(`- **${metaItem.label}**：${metaItem.value}`)
        }
        lines.push('')
    }

    const factGroups = groupFactsByType(knowledge.facts)
    if (factGroups.length > 0) {
        lines.push('---')
        lines.push('')
        for (const factGroup of factGroups) {
            const sectionTitle = FACT_TYPE_LABELS[factGroup.type] || factGroup.type
            lines.push(`## ${sectionTitle}`)
            lines.push('')
            for (const factItem of factGroup.items) {
                const factText = (factItem.text || '').trim()
                if (factText) {
                    lines.push(factText)
                    lines.push('')
                }
            }
        }
    }

    if (knowledge.artifacts?.length) {
        lines.push('---')
        lines.push('')
        lines.push('## 关联产物')
        lines.push('')
        for (const artifactItem of knowledge.artifacts) {
            const roleLabel = ARTIFACT_ROLE_LABELS[artifactItem.artifactRole] || artifactItem.artifactRole || '产物'
            const typeSuffix = artifactItem.artifactType ? ` · ${artifactItem.artifactType}` : ''
            let artifactLine = `- **${roleLabel}**${typeSuffix}`
            if (artifactItem.contentRef) {
                artifactLine += `：${artifactItem.contentRef}`
            }
            if (artifactItem.artifactUrl) {
                artifactLine += ` · [查看链接](${artifactItem.artifactUrl})`
            }
            lines.push(artifactLine)
        }
        lines.push('')
    }

    if (options.detailUrl) {
        lines.push('---')
        lines.push('')
        lines.push(`来源：[智忆](${options.detailUrl})`)
    }

    return lines.join('\n').trim()
}

/**
 * 从 route-suggestion 的 checklistHints 构建 Fact 级证据高亮索引
 * @param {Array<{ id: string, evidence?: string, evidenceSpans?: Array }>} checklistHints
 * @returns {Record<number, Array<{ checklistId: string, snippet: string, evidence: string }>>}
 */
export function buildFactEvidenceHighlightMap(checklistHints) {
    const highlightMap = {}
    if (!Array.isArray(checklistHints)) {
        return highlightMap
    }

    for (const hint of checklistHints) {
        if (!hint?.evidenceSpans?.length) {
            continue
        }
        for (const evidenceSpan of hint.evidenceSpans) {
            const factIndex = evidenceSpan?.factIndex
            if (factIndex === undefined || factIndex === null || factIndex < 0) {
                continue
            }
            if (!highlightMap[factIndex]) {
                highlightMap[factIndex] = []
            }
            highlightMap[factIndex].push({
                checklistId: evidenceSpan.checklistId || hint.id,
                snippet: evidenceSpan.snippet || '',
                evidence: hint.evidence || '',
            })
        }
    }
    return highlightMap
}

/**
 * 解析已发布知识详情页路由
 */
export function resolveKnowledgeDetailPath(knowledgeType, knowledgeId) {
    switch (knowledgeType) {
        case KNOWLEDGE_TYPES.RULE:
        case KNOWLEDGE_TYPES.WORKFLOW:
            return `/rule/${knowledgeId}`
        case KNOWLEDGE_TYPES.DECISION:
            return `/decision/${knowledgeId}`
        case KNOWLEDGE_TYPES.EXPERIENCE:
        default:
            return `/experience/${knowledgeId}`
    }
}

/**
 * 解析草稿编辑页路由（可编辑/发布/删除）；workflow 复用 rule 草稿编辑页，与详情页路由保持一致
 */
export function resolveKnowledgeDraftEditPath(knowledgeType, knowledgeId) {
    switch (knowledgeType) {
        case KNOWLEDGE_TYPES.RULE:
        case KNOWLEDGE_TYPES.WORKFLOW:
            return `/rule/drafts/${knowledgeId}`
        case KNOWLEDGE_TYPES.DECISION:
            return `/decision/drafts/${knowledgeId}`
        case KNOWLEDGE_TYPES.EXPERIENCE:
        default:
            return `/experience/drafts/${knowledgeId}`
    }
}

/**
 * 解析记忆中心列表页路由（详情/草稿页返回列表时使用）
 * @param {string} [knowledgeType] experience / rule / workflow / decision
 * @param {string} [tab] published / deprecated
 */
export function resolveMemoryCenterListRoute(knowledgeType, tab) {
    const query = {}
    if (knowledgeType) {
        query.type = knowledgeType
    }
    if (tab) {
        query.tab = tab
    }
    return { path: '/memory', query }
}
