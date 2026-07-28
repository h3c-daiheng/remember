/**
 * 巨人肩膀统一登录页 URL 拼接
 * 智忆复用 gonline-ssr 登录页，登录/注册成功后通过 redirect 参数回跳
 */

/** 本地开发默认 gonline portal 地址 */
export const DEFAULT_GONLINE_WEB_ORIGIN_DEV = 'http://localhost:3905'

/** 生产默认 gonline 站点地址 */
export const DEFAULT_GONLINE_WEB_ORIGIN_PROD = 'https://www.example.com'

/** 本地 hosts 联调时 gonline portal 默认 origin */
const LOCAL_EXAMPLE_GONLINE_DEV_ORIGIN = 'http://gonline.local.example.com:3905'

/**
 * dev 下通过 *.local.example.com 访问智忆时，推断 gonline 登录页 origin
 * 避免 env 未注入客户端时误回退 localhost:3905
 */
function resolveLocalExampleDevGonlineOrigin() {
    if (!import.meta.dev || !import.meta.client || typeof window === 'undefined') {
        return ''
    }
    const hostname = window.location.hostname || ''
    if (hostname.endsWith('.local.example.com')) {
        return LOCAL_EXAMPLE_GONLINE_DEV_ORIGIN
    }
    return ''
}

/**
 * 解析巨人肩膀 Web 站点 origin
 * 禁止回退到 window.location.origin，否则智忆 /login 会跳回本域 /login 形成死循环
 * @param {import('@nuxt/schema').RuntimeConfig} runtimeConfig
 */
export function resolveGonlineWebOrigin(runtimeConfig) {
    const configured = String(runtimeConfig?.public?.gonlineWebOrigin || '').trim()
    if (configured) {
        return configured.replace(/\/+$/, '')
    }
    const localHostsOrigin = resolveLocalExampleDevGonlineOrigin()
    if (localHostsOrigin) {
        return localHostsOrigin
    }
    if (import.meta.dev) {
        return DEFAULT_GONLINE_WEB_ORIGIN_DEV
    }
    return DEFAULT_GONLINE_WEB_ORIGIN_PROD
}

/**
 * 拼接巨人肩膀登录页地址（登录与注册均在同一页面完成）
 * @param {string} gonlineWebOrigin - gonline Web 站点 origin
 * @param {string} returnUrl - 登录成功后的完整回跳地址
 * @param {string} sourceApp - 来源应用标识（如 zhiyi）
 */
export function buildGonlineLoginUrl(gonlineWebOrigin, returnUrl, sourceApp) {
    const origin = (gonlineWebOrigin || '').replace(/\/+$/, '')
    const queryParams = new URLSearchParams()

    if (returnUrl) {
        queryParams.set('redirect', returnUrl)
    }
    if (sourceApp) {
        queryParams.set('source', sourceApp)
    }

    const queryString = queryParams.toString()
    return `${origin}/login${queryString ? `?${queryString}` : ''}`
}

/** 主站工作空间管理页路径 */
export const GONLINE_WORKSPACE_SETTINGS_PATH = '/workspace/settings'

/**
 * 拼接主站工作空间管理页地址（空间创建/切换/成员/邀请统一在主站管理）
 * @param {import('@nuxt/schema').RuntimeConfig} [runtimeConfig]
 * @returns {string}
 */
export function buildGonlineWorkspaceSettingsUrl(runtimeConfig) {
    const origin = resolveGonlineWebOrigin(runtimeConfig || useRuntimeConfig())
    return `${origin}${GONLINE_WORKSPACE_SETTINGS_PATH}`
}

/**
 * 进入工作空间设置入口。
 * 本地无 gonline 主站，改为站内跳转智忆本地 /settings 页，避免 window.open 主站 /workspace/settings 404。
 */
export function openGonlineWorkspaceSettings() {
    if (!import.meta.client) {
        return
    }
    navigateTo('/settings')
}
