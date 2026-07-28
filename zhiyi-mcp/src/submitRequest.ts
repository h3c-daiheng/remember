/**
 * memory_submit 请求规范化：统一 Experience / Rule / Workflow / Decision 提交格式
 */
import {
    normalizeRememberRequest,
    type RememberPayload,
    type RememberToolArgs,
} from './rememberRequest.js'

const ALLOWED_KNOWLEDGE_TYPES = ['experience', 'rule', 'workflow', 'decision'] as const

/** memory_submit 工具入参 */
export interface SubmitToolArgs extends RememberToolArgs {
    knowledgeType?: string
    title?: string
    facts?: Array<{ type?: string; text?: string }>
    tags?: string[]
    language?: string
    framework?: string
}

/**
 * 规范化 memory_submit 入参并转为后端 SystemEventRequest
 * - 默认 knowledgeType=experience
 * - experience 支持分字段 payload；rule/workflow/decision 支持 title+facts
 */
export function normalizeSubmitRequest(rawArgs: Record<string, unknown>): Record<string, unknown> {
    const args = { ...rawArgs } as SubmitToolArgs
    const knowledgeType = resolveKnowledgeType(args.knowledgeType)

    if (isStructuredStyleRequest(args, knowledgeType)) {
        return normalizeStructuredSubmit(args, knowledgeType)
    }

    const submitBody = normalizeRememberRequest(args as Record<string, unknown>)
    submitBody.knowledgeType = knowledgeType
    if (!submitBody.type) {
        submitBody.type = 'agent_finished'
    }
    return submitBody
}

/**
 * 判断是否为结构化类型提交（title + facts，无 experience 分字段）
 */
function isStructuredStyleRequest(args: SubmitToolArgs, knowledgeType: string): boolean {
    const payload = (args.payload || {}) as RememberPayload
    const hasExperienceFields = Boolean(
        payload.observation || payload.decision || payload.action,
    )
    const hasTitle = Boolean(String(args.title || payload.task || payload.title || '').trim())
    const hasFacts = Array.isArray(args.facts) && args.facts.length > 0
        || Array.isArray(payload.facts) && payload.facts.length > 0

    if (knowledgeType !== 'experience' && hasFacts) {
        return true
    }
    return hasTitle && hasFacts && !hasExperienceFields
}

/** 规范化 rule/workflow/decision 风格 submit */
function normalizeStructuredSubmit(args: SubmitToolArgs, knowledgeType: string): Record<string, unknown> {
    const payload = (args.payload || {}) as RememberPayload
    const title = String(args.title || payload.title || payload.task || '').trim()
    const facts = Array.isArray(args.facts)
        ? args.facts
        : Array.isArray(payload.facts)
            ? payload.facts
            : []

    const body: Record<string, unknown> = {
        type: args.type || 'agent_finished',
        knowledgeType,
        actor: args.actor,
        repository: args.repository,
        project: args.project,
        module: args.module,
        payload: {
            title,
            task: title,
            facts,
            tags: args.tags || payload.tags || [],
        },
    }

    const normalizedBody = normalizeRememberRequest({
        ...body,
        modifiedFiles: args.modifiedFiles,
        artifacts: args.artifacts,
    })
    normalizedBody.knowledgeType = knowledgeType
    return normalizedBody
}

/** 解析 knowledgeType，默认 experience */
function resolveKnowledgeType(knowledgeType: unknown): string {
    const normalizedType = String(knowledgeType || 'experience').trim()
    if (!ALLOWED_KNOWLEDGE_TYPES.includes(normalizedType as (typeof ALLOWED_KNOWLEDGE_TYPES)[number])) {
        throw new Error(`knowledgeType 仅支持 ${ALLOWED_KNOWLEDGE_TYPES.join('、')}`)
    }
    return normalizedType
}
