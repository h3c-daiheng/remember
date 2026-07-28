#!/usr/bin/env node
/**
 * 智忆 MCP Server：为 Cursor 等 Agent 提供 memory_recall / memory_submit / memory_feedback 工具
 */
import { Server } from '@modelcontextprotocol/sdk/server/index.js'
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js'
import {
    CallToolRequestSchema,
    ListToolsRequestSchema,
} from '@modelcontextprotocol/sdk/types.js'
import { resolveMemoryApiBase } from './apiBase.js'
import { bakedApiBase } from './config.generated.js'
import { rememberArtifactsSchema, rememberModifiedFilesSchema } from './artifactSchema.js'
import { normalizeSubmitRequest } from './submitRequest.js'

/** 构建时写入的后端根地址；运行时可通过 BIGAPP_API_BASE 覆盖（高级用法） */
const apiBase = resolveMemoryApiBase(
    process.env.BIGAPP_API_BASE || bakedApiBase,
)
/** 工作空间签发的 Agent 密钥 */
const apiKey = process.env.BIGAPP_API_KEY || ''

/** 后端统一响应结构 */
interface MemoryApiResponse {
    code: number
    message?: string
    data?: unknown
}

/**
 * 生成调用链 trace ID，与后端 X-Trace-Id 对齐，便于闭环追踪
 */
function generateTraceId(): string {
    return `tr_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
}

/**
 * 调用后端 Memory API
 * @param path API 路径后缀，如 /recall
 * @param body 请求体 JSON 对象
 */
async function callMemoryApi(path: string, body: Record<string, unknown>): Promise<unknown> {
    const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Trace-Id': generateTraceId(),
    }
    if (apiKey) {
        headers.Authorization = `Bearer ${apiKey}`
    }
    const response = await fetch(`${apiBase}/memory${path}`, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
    })
    const responseText = await response.text()
    let result: MemoryApiResponse
    try {
        result = JSON.parse(responseText) as MemoryApiResponse
    } catch {
        const preview = responseText.replace(/\s+/g, ' ').slice(0, 160)
        throw new Error(
            `API 响应非 JSON（HTTP ${response.status}，URL ${apiBase}/memory${path}）: ${preview || '(empty)'}`,
        )
    }
    if (result.code !== 0) {
        throw new Error(result.message || 'API 请求失败')
    }
    return result.data
}

const FACT_BLOCK_TYPE_ENUM = [
    'observation',
    'decision',
    'constraint',
    'rule',
    'evidence',
    'action',
    'outcome',
]

const SUBMIT_PAYLOAD_SCHEMA = {
    type: 'object',
    description: '记忆内容；experience 推荐分字段填写，rule/workflow/decision 可用 title+facts',
    properties: {
        task: {
            type: 'string',
            description: '任务简述 / 标题（≤80字），不要当作 observation',
        },
        title: {
            type: 'string',
            description: '知识标题（rule/workflow/decision 时使用，可与 task 二选一）',
        },
        observation: {
            type: 'string',
            description: '观察：问题现象、环境差异、报错信息等（experience 必填）',
        },
        decision: {
            type: 'string',
            description: '决策：选了什么方案、为什么（experience 必填）',
        },
        action: {
            type: 'string',
            description: '行动：具体改动（experience 必填；workflow 可为有序步骤）',
        },
        outcome: {
            type: 'string',
            description: '结果：是否解决、如何验证',
        },
        constraint: {
            type: 'string',
            description: '约束：适用/失效条件',
        },
        facts: {
            type: 'array',
            description: 'Fact Block 列表（rule/workflow/decision 必填；experience 可选）',
            items: {
                type: 'object',
                properties: {
                    type: { type: 'string', enum: FACT_BLOCK_TYPE_ENUM },
                    text: { type: 'string' },
                },
                required: ['type', 'text'],
            },
        },
        tags: { type: 'array', items: { type: 'string' }, description: '标签' },
    },
}

const server = new Server(
    { name: 'zhiyi-memory', version: '1.0.0' },
    { capabilities: { tools: {} } },
)

/** 注册 MCP 工具列表 */
server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: [
        {
            name: 'memory_recall',
            description:
                '根据任务上下文召回相关经验 Fact Blocks。每条返回 reliability 档位：'
                + 'reliable 可直接采纳；uncertain 须结合代码核实后再用；low 仅作线索必须验证。'
                + '核实通过请调 memory_feedback 回填 helpful，不符回填 outdated/wrong，使用后回填 used。',
            inputSchema: {
                type: 'object',
                properties: {
                    task: { type: 'string', description: '当前任务描述' },
                    repository: { type: 'string', description: '代码仓库' },
                    project: { type: 'string', description: '项目名' },
                    module: { type: 'string', description: '模块名' },
                    currentFile: { type: 'string', description: '当前文件路径' },
                    modifiedFiles: { type: 'array', items: { type: 'string' } },
                    factTypes: { type: 'array', items: { type: 'string' } },
                    knowledgeTypes: {
                        type: 'array',
                        items: { type: 'string' },
                        description: '召回的知识类型；未传时 MCP 默认 rule/workflow/experience（Recall 优先 Rule）',
                    },
                    limit: { type: 'number', description: '返回条数上限' },
                    expandGraph: {
                        type: 'boolean',
                        description: '是否启用 Hybrid Recall：向量种子 + 图谱扩展邻接经验',
                    },
                    graphDepth: {
                        type: 'number',
                        description: '图谱扩展跳数，默认 2，最大 3',
                    },
                    graphRelationTypes: {
                        type: 'array',
                        items: { type: 'string' },
                        description: '图谱扩展使用的关系类型；默认 depends_on / related_semantic / related_decision',
                    },
                },
                required: ['task'],
            },
        },
        {
            name: 'memory_graph_explore',
            description: '以指定经验为中心探索关系子图，返回节点与边 JSON 供 Agent 导航',
            inputSchema: {
                type: 'object',
                properties: {
                    centerId: { type: 'number', description: '中心节点 knowledge.id' },
                    depth: { type: 'number', description: '扩展跳数，默认 2，最大 3' },
                    limit: { type: 'number', description: '返回边数上限，默认 50' },
                    relationTypes: {
                        type: 'array',
                        items: { type: 'string' },
                        description: '关系类型过滤，空则返回全部可见边',
                    },
                },
                required: ['centerId'],
            },
        },
        {
            name: 'memory_submit',
            description:
                'Agent 统一提交记忆，全部进入 Capture 草稿供人工确认发布。' +
                'knowledgeType 可选 experience/rule/workflow/decision，默认 experience。' +
                'experience：须 observation+decision+action 或 facts；' +
                'rule/workflow/decision：须 title（或 task）+ facts。' +
                '【提交门】根因是未遵守既有规范时，experience 不要提交；规范缺口应提交 rule/workflow。' +
                'artifacts 须含 artifactType/artifactRole/contentRef；也可传 modifiedFiles。' +
                '详见本包 README「memory_submit」提交门说明',
            inputSchema: {
                type: 'object',
                properties: {
                    knowledgeType: {
                        type: 'string',
                        enum: ['experience', 'rule', 'workflow', 'decision'],
                        description: '记忆类型，默认 experience',
                    },
                    type: { type: 'string', description: '事件类型，默认 agent_finished' },
                    actor: { type: 'string', description: 'Agent 标识' },
                    workspace: {
                        type: 'string',
                        description: '工作空间编码（可选，默认使用 API Key 所属工作空间；勿传本地目录路径）',
                    },
                    repository: { type: 'string', description: '代码仓库名' },
                    project: { type: 'string', description: '业务项目名' },
                    module: { type: 'string', description: '模块名' },
                    title: { type: 'string', description: '标题（rule/workflow/decision 时使用）' },
                    modifiedFiles: rememberModifiedFilesSchema,
                    payload: SUBMIT_PAYLOAD_SCHEMA,
                    facts: SUBMIT_PAYLOAD_SCHEMA.properties.facts,
                    tags: { type: 'array', items: { type: 'string' }, description: '标签' },
                    artifacts: rememberArtifactsSchema,
                },
            },
        },
        {
            name: 'memory_feedback',
            description:
                '对 Recall 结果提交效果反馈。uncertain/low 档经验结合代码核实后：'
                + '通过则回填 helpful（提升该经验置信度），不符回填 outdated/wrong（降权并可能触发治理工单）；'
                + 'reliable 档采纳使用后回填 used。feedbackType：used/helpful/not_helpful/outdated/wrong。',
            inputSchema: {
                type: 'object',
                properties: {
                    sessionId: { type: 'string', description: 'Recall 会话 ID' },
                    knowledgeId: { type: 'number', description: '知识 ID' },
                    feedbackType: {
                        type: 'string',
                        enum: ['used', 'helpful', 'not_helpful', 'outdated', 'wrong'],
                    },
                    comment: { type: 'string' },
                },
                required: ['knowledgeId', 'feedbackType'],
            },
        },
    ],
}))

/** Beta 起 Recall 默认召回 Rule / Workflow / Experience，Ranking 对 Rule 有 typeBoost 加权 */
const DEFAULT_RECALL_KNOWLEDGE_TYPES = ['rule', 'workflow', 'experience']

/**
 * 规范化 memory_recall 请求：未传 knowledgeTypes 时注入 Beta 默认值
 */
function normalizeRecallRequest(args: Record<string, unknown>): Record<string, unknown> {
    const normalized = { ...args }
    const knowledgeTypes = normalized.knowledgeTypes
    if (!Array.isArray(knowledgeTypes) || knowledgeTypes.length === 0) {
        normalized.knowledgeTypes = DEFAULT_RECALL_KNOWLEDGE_TYPES
    }
    return normalized
}

/** 处理 MCP 工具调用 */
server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const toolName = request.params.name
    const args = request.params.arguments || {}

    if (toolName === 'memory_recall') {
        const normalizedArgs = normalizeRecallRequest(args as Record<string, unknown>)
        const data = await callMemoryApi('/recall', normalizedArgs)
        return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
    }

    if (toolName === 'memory_graph_explore') {
        const data = await callMemoryApi('/graph-explore', args as Record<string, unknown>)
        return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
    }

    if (toolName === 'memory_submit') {
        const normalizedArgs = normalizeSubmitRequest(args as Record<string, unknown>)
        const data = await callMemoryApi('/submit', normalizedArgs)
        return { content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] }
    }

    if (toolName === 'memory_feedback') {
        await callMemoryApi('/feedback', args)
        return { content: [{ type: 'text', text: 'Feedback recorded successfully' }] }
    }

    throw new Error(`Unknown tool: ${toolName}`)
})

async function main() {
    const transport = new StdioServerTransport()
    await server.connect(transport)
}

main().catch((error) => {
    console.error('MCP server failed:', error)
    process.exit(1)
})
