import { getStoredToken } from '~/utils/token'

/**
 * 拼接浏览器 / SSR 同源 gonline API 相对路径（/gonline-api 前缀）。
 * - 浏览器：经 nginx 或 Nitro server/routes/gonline-api 转发
 * - SSR：经 useRequestFetch 走同源 Nitro 代理（避免 Node 直连不可达地址）
 * @param {string} path 如 /user/self
 * @returns {string}
 */
function buildGonlineApiBrowserPath(path) {
    const runtimeConfig = useRuntimeConfig()
    const base = String(runtimeConfig.public.gonlineApiBase || '/gonline-api').replace(/\/$/, '')
    const suffix = path.startsWith('/') ? path : `/${path}`
    return `${base}${suffix}`
}

/**
 * 是否为相对 API 基址（同源路径，可走 Nitro 内部请求）。
 * @param {string} apiPath
 * @returns {boolean}
 */
function isRelativeGonlineApiPath(apiPath) {
    return typeof apiPath === 'string' && apiPath.startsWith('/')
}

/**
 * SSR 经 useRequestFetch 走同源 /gonline-api → server/routes/gonline-api/[...].js 代理；
 * CSR 使用原生 fetch 相对路径（经 nginx / Nitro 转发）。
 * @param {string} path 如 /dict/info/dict-code?...
 * @param {RequestInit & { headers?: Record<string, string> }} [options]
 * @returns {Promise<any>}
 */
async function fetchGonlineApiRaw(path, options = {}) {
    const apiPath = buildGonlineApiBrowserPath(path)
    const headers = { ...(options.headers || {}) }

    // SSR：useRequestFetch 命中本机 Nitro 代理，对齐 gonline-ssr axios.requestViaSsrFetch
    if (import.meta.server && isRelativeGonlineApiPath(apiPath)) {
        const requestFetch = useRequestFetch()
        return requestFetch(apiPath, {
            method: options.method || 'GET',
            headers,
            body: options.body,
            // 业务码可能非 2xx HTTP，仍要拿到 JSON 体给上层判断
            ignoreResponseError: true,
        })
    }

    const response = await fetch(apiPath, {
        ...options,
        headers,
    })
    return response.json()
}

/**
 * 解析 gonline API 绝对 URL（埋点 unload keepalive / sendBeacon 需要完整地址）
 * @param {string} path
 * @returns {string}
 */
export function resolveGonlineApiAbsoluteUrl(path) {
    const relativePath = buildGonlineApiBrowserPath(path)
    if (typeof window === 'undefined') {
        return relativePath
    }
    try {
        return new URL(relativePath, window.location.origin).href
    } catch (_) {
        return relativePath
    }
}

/**
 * 调用巨人肩膀 gonline API（成功码 code === 200，对齐 chat2x-web 使用 fetch）
 */
export async function gonlineApiRequest(path, options = {}) {
    const token = getStoredToken()
    const headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
    }

    if (token) {
        headers.Authorization = `Bearer ${token}`
    }

    const result = await fetchGonlineApiRaw(path, {
        ...options,
        headers,
    })

    if (Number(result?.code) !== 200) {
        throw new Error(result?.message || '请求失败')
    }
    return result.data
}

/**
 * 埋点专用：静默请求，失败不抛错（避免影响业务）
 * 关于/联系等 SSR 页拉字典也走此方法
 * @param {string} path
 * @param {RequestInit} [options]
 * @returns {Promise<any>}
 */
export async function gonlineApiRequestSilent(path, options = {}) {
    try {
        const token = getStoredToken()
        const headers = {
            'Content-Type': 'application/json',
            ...(options.headers || {}),
        }
        if (token) {
            headers.Authorization = `Bearer ${token}`
        }
        const result = await fetchGonlineApiRaw(path, {
            ...options,
            headers,
        })
        return result ?? null
    } catch (_) {
        return null
    }
}

/**
 * 获取当前登录用户的完整资料（gonline /user/self）
 */
export function fetchGonlineUserSelf() {
    return gonlineApiRequest('/user/self')
}

/**
 * 获取客户端 IP（埋点用，走 gonline /ip）
 * @returns {Promise<any>}
 */
export function fetchGonlineClientIp() {
    return gonlineApiRequestSilent('/ip', { method: 'GET' })
}
