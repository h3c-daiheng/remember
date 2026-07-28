/**
 * memory_submit 请求规范化：补全 Artifact、剔除空对象、从 action/modifiedFiles 推断关联文件
 */

/** 与后端 ArtifactDto 对齐的产物结构 */
export interface RememberArtifact {
    artifactType: string
    artifactRole: string
    contentRef: string
    artifactUrl?: string
}

/** memory_submit 工具入参（Event 结构） */
export interface RememberToolArgs {
    type?: string
    actor?: string
    workspace?: string
    repository?: string
    project?: string
    module?: string
    modifiedFiles?: string[]
    payload?: RememberPayload
    artifacts?: unknown[]
    [key: string]: unknown
}

/** 经验载荷，对应后端 Event payload */
export interface RememberPayload {
    task?: string
    observation?: string
    decision?: string
    action?: string
    outcome?: string
    constraint?: string
    tags?: string[]
    facts?: Array<{ type?: string; text?: string }>
    [key: string]: unknown
}

const ARTIFACT_TYPE_VALUES = [
    'commit',
    'pr',
    'issue',
    'conversation',
    'code',
    'test',
    'deploy',
    'manual',
] as const

const ARTIFACT_ROLE_VALUES = ['origin', 'evidence', 'attachment', 'reference'] as const

/** 从 action 等文本中提取疑似文件路径（如 zhiyi-web/utils/token.js） */
const FILE_PATH_PATTERN = /(?:[\w.-]+\/)+[\w.-]+\.\w+/g

/**
 * 规范化 memory_submit Event 入参后再转发后端
 * - 剔除空 Artifact 对象
 * - 合并 modifiedFiles、action 中推断出的文件路径
 */
export function normalizeRememberRequest(rawArgs: Record<string, unknown>): Record<string, unknown> {
    const args = { ...rawArgs } as RememberToolArgs
    const payload = normalizePayload(args.payload)

    const artifacts = mergeArtifacts(
        normalizeArtifacts(args.artifacts),
        buildArtifactsFromFilePaths(args.modifiedFiles),
        extractArtifactsFromText(typeof payload.action === 'string' ? payload.action : ''),
    )

    const body: Record<string, unknown> = { ...args, payload }

    // workspace 在后端是编码（如 default），Agent 误传本地目录会导致 403
    const sanitizedWorkspace = sanitizeWorkspaceCode(args.workspace)
    if (sanitizedWorkspace) {
        body.workspace = sanitizedWorkspace
    } else {
        delete body.workspace
    }

    if (artifacts.length > 0) {
        body.artifacts = artifacts
    } else {
        delete body.artifacts
    }

    // modifiedFiles 写入 metadata，供后端在 artifacts 为空时推断
    if (Array.isArray(args.modifiedFiles) && args.modifiedFiles.length > 0) {
        const metadata =
            args.metadata && typeof args.metadata === 'object' && !Array.isArray(args.metadata)
                ? { ...(args.metadata as Record<string, unknown>) }
                : {}
        if (!Array.isArray(metadata.modifiedFiles)) {
            metadata.modifiedFiles = args.modifiedFiles
        }
        body.metadata = metadata
    }
    delete body.modifiedFiles
    return body
}

/** 确保 payload 为对象，便于后续读取 action 等字段 */
function normalizePayload(payload: unknown): RememberPayload {
    if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
        return { ...(payload as RememberPayload) }
    }
    return {}
}

/**
 * 将 Agent 传入的 artifacts 转为有效结构
 * 兼容 type/role/path 等常见别名，丢弃无实质内容的空对象
 */
function normalizeArtifacts(rawArtifacts: unknown): RememberArtifact[] {
    if (!Array.isArray(rawArtifacts)) {
        return []
    }
    const normalizedList: RememberArtifact[] = []
    for (const item of rawArtifacts) {
        const normalized = normalizeArtifactItem(item)
        if (normalized) {
            normalizedList.push(normalized)
        }
    }
    return normalizedList
}

/** 单条 Artifact 规范化，字段缺失时尝试别名映射 */
function normalizeArtifactItem(item: unknown): RememberArtifact | null {
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
        return null
    }
    const record = item as Record<string, unknown>
    const artifactType = readNonEmptyString(
        record.artifactType,
        record.type,
        record.artifact_type,
    )
    const artifactRole = readNonEmptyString(
        record.artifactRole,
        record.role,
        record.artifact_role,
    ) || 'origin'
    const contentRef = readNonEmptyString(
        record.contentRef,
        record.path,
        record.file,
        record.content_ref,
        record.ref,
    )
    const artifactUrl = readNonEmptyString(record.artifactUrl, record.url)

    if (!artifactType && !contentRef && !artifactUrl) {
        return null
    }

    const resolvedType = resolveArtifactType(artifactType, contentRef || artifactUrl)
    const resolvedContentRef = contentRef || artifactUrl || ''
    const result: RememberArtifact = {
        artifactType: resolvedType,
        artifactRole: resolveArtifactRole(artifactRole),
        contentRef: resolvedContentRef,
    }
    if (artifactUrl && artifactUrl !== resolvedContentRef) {
        result.artifactUrl = artifactUrl
    }
    return result
}

/** 根据文件扩展名推断 artifactType，默认 code */
function resolveArtifactType(explicitType: string, reference: string): string {
    if (explicitType) {
        if (explicitType === 'file') {
            return 'code'
        }
        if (ARTIFACT_TYPE_VALUES.includes(explicitType as (typeof ARTIFACT_TYPE_VALUES)[number])) {
            return explicitType
        }
        return explicitType
    }
    if (/\.(md|txt|doc|docx)$/i.test(reference)) {
        return 'manual'
    }
    if (/\.(test|spec)\./i.test(reference)) {
        return 'test'
    }
    return 'code'
}

/** 校验 artifactRole，非法值回退为 origin */
function resolveArtifactRole(role: string): string {
    if (ARTIFACT_ROLE_VALUES.includes(role as (typeof ARTIFACT_ROLE_VALUES)[number])) {
        return role
    }
    return 'origin'
}

/** 从修改文件列表构建 origin 类型 Artifact */
function buildArtifactsFromFilePaths(filePaths: string[] | undefined): RememberArtifact[] {
    if (!Array.isArray(filePaths)) {
        return []
    }
    const artifacts: RememberArtifact[] = []
    const seenReferences = new Set<string>()
    for (const filePath of filePaths) {
        const normalizedPath = typeof filePath === 'string' ? filePath.trim() : ''
        if (!normalizedPath || seenReferences.has(normalizedPath)) {
            continue
        }
        seenReferences.add(normalizedPath)
        artifacts.push({
            artifactType: resolveArtifactType('', normalizedPath),
            artifactRole: 'origin',
            contentRef: normalizedPath,
        })
    }
    return artifacts
}

/** 从 action 描述中提取文件路径并转为 Artifact */
function extractArtifactsFromText(actionText: string): RememberArtifact[] {
    if (!actionText.trim()) {
        return []
    }
    const matches = actionText.match(FILE_PATH_PATTERN) || []
    const artifacts: RememberArtifact[] = []
    const seenReferences = new Set<string>()
    for (const match of matches) {
        const normalizedPath = match.trim()
        if (!normalizedPath || seenReferences.has(normalizedPath)) {
            continue
        }
        seenReferences.add(normalizedPath)
        artifacts.push({
            artifactType: resolveArtifactType('', normalizedPath),
            artifactRole: 'origin',
            contentRef: normalizedPath,
        })
    }
    return artifacts
}

/**
 * 合并多组 Artifact，按 contentRef 去重
 */
function mergeArtifacts(...groups: RememberArtifact[][]): RememberArtifact[] {
    const merged: RememberArtifact[] = []
    const seenReferences = new Set<string>()
    for (const group of groups) {
        for (const artifact of group) {
            const dedupeKey = `${artifact.artifactRole}:${artifact.contentRef}`
            if (seenReferences.has(dedupeKey)) {
                continue
            }
            seenReferences.add(dedupeKey)
            merged.push(artifact)
        }
    }
    return merged
}

/**
 * 校验 workspace 是否为合法编码
 * 含路径分隔符的值视为 Agent 误传的本地目录，交由后端按 API Key 默认工作空间处理
 */
function sanitizeWorkspaceCode(workspace: unknown): string | undefined {
    if (typeof workspace !== 'string') {
        return undefined
    }
    const trimmedWorkspace = workspace.trim()
    if (!trimmedWorkspace || trimmedWorkspace.includes('/') || trimmedWorkspace.includes('\\')) {
        return undefined
    }
    return trimmedWorkspace
}

/** 依次读取候选值，返回首个非空字符串 */
function readNonEmptyString(...candidates: unknown[]): string {
    for (const candidate of candidates) {
        if (candidate === null || candidate === undefined) {
            continue
        }
        const text = String(candidate).trim()
        if (text) {
            return text
        }
    }
    return ''
}
