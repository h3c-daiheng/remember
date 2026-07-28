/**
 * 解析 Memory API 根地址
 * - 本地 / 内网直连 zhiyi-server（4302/4312/4322）：不含 /api
 * - 经 nginx 同域代理的 test/pro 域名：须带 /api 前缀
 */
const DIRECT_SERVER_PORTS = new Set(['4302', '4312', '4322'])

/**
 * 根据 BIGAPP_API_BASE 推断实际请求前缀
 * 公网域名未写 /api 时自动补全，兼容旧配置
 */
export function resolveMemoryApiBase(rawBase: string): string {
    const normalizedBase = rawBase.replace(/\/$/, '')
    if (normalizedBase.endsWith('/api')) {
        return normalizedBase
    }

    try {
        const parsedUrl = new URL(normalizedBase)
        const isLocalHost =
            parsedUrl.hostname === '127.0.0.1' || parsedUrl.hostname === 'localhost'
        const isDirectServerPort = DIRECT_SERVER_PORTS.has(parsedUrl.port)

        if (isLocalHost || isDirectServerPort) {
            return normalizedBase
        }

        return `${normalizedBase}/api`
    } catch {
        return normalizedBase
    }
}
