import { useTokenCookie } from '~/utils/tokenCookie'
import { buildGonlineLoginUrl, resolveGonlineWebOrigin } from '~/utils/gonlineAuthLogin'

/**
 * 读取 gonline 与智忆共用的 access_token（Cookie）
 */
export function getStoredToken() {
    if (!import.meta.client) {
        return ''
    }
    const tokenCookie = useTokenCookie()
    return tokenCookie.value || ''
}

/**
 * 写入 access_token 到 Cookie（一般由 gonline 登录页写入，智忆侧仅兜底）
 */
export function saveStoredToken(token) {
    if (!import.meta.client || !token) {
        return
    }
    const tokenCookie = useTokenCookie()
    tokenCookie.value = token
}

/**
 * 清除本地 access_token
 */
export function clearStoredToken() {
    if (!import.meta.client) {
        return
    }
    const tokenCookie = useTokenCookie()
    tokenCookie.value = null
}

/**
 * 构建登录成功后的回跳地址；登录页本身不能作为回跳目标，否则会再次触发统一登录跳转
 */
function buildZhiyiReturnUrl(redirectPath) {
    const currentPath = redirectPath
        || `${window.location.pathname}${window.location.search}${window.location.hash}`

    let normalizedPath = currentPath
    if (currentPath.startsWith('http://') || currentPath.startsWith('https://')) {
        try {
            const parsedUrl = new URL(currentPath)
            normalizedPath = `${parsedUrl.pathname}${parsedUrl.search}${parsedUrl.hash}`
        } catch (error) {
            normalizedPath = '/'
        }
    }

    const isLoginPath = normalizedPath === '/login' || normalizedPath.startsWith('/login?')
    const safePath = isLoginPath ? '/memory' : currentPath

    if (safePath.startsWith('http://') || safePath.startsWith('https://')) {
        return safePath
    }

    return `${window.location.origin}${safePath.startsWith('/') ? safePath : `/${safePath}`}`
}

/**
 * 跳转本地登录页，登录成功后回跳到指定地址
 */
export function redirectToGonlineLogin(redirectPath) {
    if (!import.meta.client) {
        return
    }
    const currentPath = redirectPath
        || `${window.location.pathname}${window.location.search}${window.location.hash}`
    const isLoginPath = currentPath === '/login' || currentPath.startsWith('/login?')
    const safePath = isLoginPath ? '/memory' : currentPath
    window.location.href = `/login?redirect=${encodeURIComponent(safePath)}`
}

/**
 * 退出后跳转智忆首页（Cookie 已清除，页面以未登录态展示）
 */
export function redirectAfterLogout() {
    if (!import.meta.client) {
        return
    }
    window.location.href = '/'
}
