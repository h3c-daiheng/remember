import { saveStoredToken } from '~/utils/token'

/**
 * 从 URL hash 或 query 中提取 gonline 回传的 access_token 并写入本地 Cookie
 * 用于 localhost 等不同端口无法共享 Cookie 时的 SSO 回跳
 * @returns {boolean} 是否成功提取并写入 token
 */
export function bootstrapTokenFromUrl() {
    if (!import.meta.client || typeof window === 'undefined') {
        return false
    }

    let accessToken = ''

    const hashContent = (window.location.hash || '').replace(/^#/, '')
    if (hashContent) {
        const hashParams = new URLSearchParams(hashContent)
        accessToken = hashParams.get('token') || hashParams.get('access_token') || ''
    }

    if (!accessToken) {
        const queryParams = new URLSearchParams(window.location.search)
        accessToken = queryParams.get('token') || queryParams.get('access_token') || ''
    }

    if (!accessToken) {
        return false
    }

    saveStoredToken(accessToken)

    const cleanUrl = `${window.location.pathname}${window.location.search}`
    window.history.replaceState({}, '', cleanUrl)
    return true
}
