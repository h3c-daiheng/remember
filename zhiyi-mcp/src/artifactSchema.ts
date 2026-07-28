/**
 * memory_submit 工具 schema 片段：Artifact 与 modifiedFiles 定义
 */

/** Artifact 数组 schema，要求填写类型、角色与引用，禁止传空对象 */
export const rememberArtifactsSchema = {
    type: 'array',
    description:
        '关联产物（修改的文件、PR 等）。每项必须填写 artifactType、artifactRole、contentRef，' +
        '禁止传 {} 空对象；若不传，服务端会尝试从 action 与 modifiedFiles 推断',
    items: {
        type: 'object',
        properties: {
            artifactType: {
                type: 'string',
                enum: ['commit', 'pr', 'issue', 'conversation', 'code', 'test', 'deploy', 'manual'],
                description: '产物类型，代码文件用 code',
            },
            artifactRole: {
                type: 'string',
                enum: ['origin', 'evidence', 'attachment', 'reference'],
                description: '角色，代码改动通常为 origin',
            },
            contentRef: {
                type: 'string',
                description: '文件路径、PR 号、文档路径等引用',
            },
            artifactUrl: {
                type: 'string',
                description: '外链地址（可选）',
            },
        },
        required: ['artifactType', 'artifactRole', 'contentRef'],
    },
} as const

/** 修改文件列表，MCP 侧会自动转为 code/origin Artifact */
export const rememberModifiedFilesSchema = {
    type: 'array',
    items: { type: 'string' },
    description: '本次任务修改过的文件路径；会自动转为 Artifact，并写入 metadata 供后端推断',
} as const
