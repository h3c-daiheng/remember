/**
 * HTTP 请求层：统一 API 基址、gonline OAuth token 携带、响应解析（对齐 chat2x-web 使用 fetch）
 */
import { getStoredToken } from '~/utils/token'

/**
 * 发起 API 请求并解析统一响应结构 { code, message, data }
 */
export async function apiRequest(path, options = {}) {
    const runtimeConfig = useRuntimeConfig()
    const token = getStoredToken()
    const headers = {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
    }

    if (token) {
        headers.Authorization = `Bearer ${token}`
    }

    const response = await fetch(`${runtimeConfig.public.apiBase}${path}`, {
        ...options,
        headers,
    })

    const result = await response.json()
    if (result.code !== 0) {
        throw new Error(result.message || '请求失败')
    }
    return result.data
}

export { clearStoredToken, getStoredToken, redirectAfterLogout, redirectToGonlineLogin, saveStoredToken } from '~/utils/token'
