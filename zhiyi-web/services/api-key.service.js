/**
 * Agent API Key 管理 API：本地通道 /api/workspace/api-keys（与本地账号登录同源，code=0）
 * 列表/签发/吊销均在本地，不依赖 gonline 主站
 */
import { apiRequest } from '~/services/http'

/**
 * 获取当前（或指定）工作空间的 Agent API Key 列表
 * @param {string} [workspaceId]
 * @returns {Promise<{ keyList: Array }>}
 */
export function fetchApiKeyListRequest(workspaceId) {
    const query = workspaceId
        ? `?workspaceId=${encodeURIComponent(workspaceId)}`
        : ''
    return apiRequest(`/workspace/api-keys${query}`)
}

/**
 * 签发新 Agent API Key（仅 owner/admin）
 * @param {{ keyName: string, permissionRecall?: boolean, permissionRemember?: boolean }} payload
 * @returns {Promise<{ plainKey: string, id: number, keyName: string, keyPrefix: string, enabled: boolean }>}
 */
export function createApiKeyRequest(payload) {
    return apiRequest('/workspace/api-keys', {
        method: 'POST',
        body: JSON.stringify(payload || {}),
    })
}

/**
 * 吊销 Agent API Key（仅 owner/admin）
 * @param {number|string} id
 */
export function revokeApiKeyRequest(id) {
    return apiRequest(`/workspace/api-keys/${id}`, {
        method: 'DELETE',
    })
}
