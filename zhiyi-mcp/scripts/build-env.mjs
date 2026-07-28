#!/usr/bin/env node
/**
 * 按环境读取 .env.{env}，生成 src/config.generated.ts
 * 供 npm run build / build:test / build:production 在 tsc 前注入 BIGAPP_API_BASE
 */
import { readFileSync, writeFileSync } from 'fs'
import { dirname, resolve } from 'path'
import { fileURLToPath } from 'url'

const packageRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const envName = process.argv[2] || process.env.ZHIYI_MCP_ENV || 'development'
const envFilePath = resolve(packageRoot, `.env.${envName}`)

/**
 * 解析 KEY=VALUE 格式的 env 文件（忽略空行与 # 注释）
 * @param {string} content 文件内容
 * @returns {Record<string, string>}
 */
function parseEnvFile(content) {
    const variables = {}
    for (const line of content.split('\n')) {
        const trimmedLine = line.trim()
        if (!trimmedLine || trimmedLine.startsWith('#')) {
            continue
        }
        const separatorIndex = trimmedLine.indexOf('=')
        if (separatorIndex === -1) {
            continue
        }
        const key = trimmedLine.slice(0, separatorIndex).trim()
        let value = trimmedLine.slice(separatorIndex + 1).trim()
        if (
            (value.startsWith('"') && value.endsWith('"'))
            || (value.startsWith("'") && value.endsWith("'"))
        ) {
            value = value.slice(1, -1)
        }
        variables[key] = value
    }
    return variables
}

let envContent
try {
    envContent = readFileSync(envFilePath, 'utf8')
} catch (error) {
    console.error(`[zhiyi-mcp] 未找到环境文件: .env.${envName}`)
    process.exit(1)
}

const variables = parseEnvFile(envContent)
const apiBase = variables.BIGAPP_API_BASE
if (!apiBase) {
    console.error(`[zhiyi-mcp] .env.${envName} 缺少 BIGAPP_API_BASE`)
    process.exit(1)
}

const generatedContent = `/**
 * 构建时自动生成，请勿手动编辑
 * 环境: ${envName}
 * 来源: .env.${envName}
 */
export const buildEnv = '${envName}'
export const bakedApiBase = '${apiBase.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'
`

writeFileSync(resolve(packageRoot, 'src/config.generated.ts'), generatedContent)
console.log(`[zhiyi-mcp] 已加载 .env.${envName}，BIGAPP_API_BASE=${apiBase}`)
