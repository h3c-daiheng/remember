/** 与 gonline 共用的 OAuth access_token Cookie 键名 */
export const TOKEN_COOKIE_KEY = 'token'

/** token Cookie 有效期：7 天 */
export const TOKEN_COOKIE_MAX_AGE = 60 * 60 * 24 * 7

/**
 * 从 runtimeConfig 解析 token Cookie 的父域（跨子域 SSO，如 .example.com）
 */
export function resolveTokenCookieDomain(runtimeConfig) {
    const domain = String(runtimeConfig?.public?.cookieDomain || '').trim()
    return domain || undefined
}

/**
 * 构建 token Cookie 选项；写入、读取、清除须使用同一套选项（含 domain）
 */
export function buildTokenCookieOptions(cookieDomain) {
    const options = {
        maxAge: TOKEN_COOKIE_MAX_AGE,
        sameSite: 'strict',
        httpOnly: false,
        path: '/',
    }
    const domain = String(cookieDomain || '').trim()
    if (domain) {
        options.domain = domain
    }
    return options
}

/**
 * 读写 gonline 与智忆共用的 access_token Cookie
 */
export function useTokenCookie() {
    const runtimeConfig = useRuntimeConfig()
    const options = buildTokenCookieOptions(resolveTokenCookieDomain(runtimeConfig))
    return useCookie(TOKEN_COOKIE_KEY, options)
}
