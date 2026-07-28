/**
 * 智忆认证 API：获取带工作空间上下文的当前用户
 */
import { apiRequest } from '~/services/http'

/**
 * 获取当前登录用户（含 workspaceId、memberRole 等智忆上下文）
 */
export function fetchAuthMeRequest() {
    return apiRequest('/auth/me')
}

/**
 * 本地账号密码登录，返回 { token, user }
 */
export function loginRequest(username, password) {
    return apiRequest('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
    })
}
